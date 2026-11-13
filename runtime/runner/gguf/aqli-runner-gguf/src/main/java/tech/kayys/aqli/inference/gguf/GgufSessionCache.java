package tech.kayys.aqli.inference.gguf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Owns daemon-scoped GGUF sessions and Java backend sessions. It centralizes
 * exact/compatible reuse, access-order eviction, one-shot leases, and shutdown.
 * Session limits, library paths, and logging are supplied by the application boundary.
 * The session limit is a count cap, not an estimate of native memory consumption.
 * Cache operations are serialized; callers must stop request dispatch and release
 * borrowed session leases before closing the cache.
 */
public final class GgufSessionCache implements AutoCloseable {
    private final IntSupplier maxSessionsSupplier;
    private final Consumer<String> log;
    private final LlamaCppGgufLibraryPaths libraryPaths;
    private final SessionOpener sessionOpener;
    private boolean closed;

    public GgufSessionCache(IntSupplier maxSessionsSupplier, Consumer<String> log,
            LlamaCppGgufLibraryPaths libraryPaths) {
        this(maxSessionsSupplier, log, libraryPaths, LlamaCppGgufSession::open);
    }

    GgufSessionCache(IntSupplier maxSessionsSupplier, Consumer<String> log,
            LlamaCppGgufLibraryPaths libraryPaths, SessionOpener sessionOpener) {
        this.maxSessionsSupplier = maxSessionsSupplier;
        this.log = log;
        this.libraryPaths = libraryPaths;
        this.sessionOpener = sessionOpener;
    }

    record SessionKey(Path modelPath, LlamaCppGgufSessionParameters parameters) {
    }

    @FunctionalInterface
    interface SessionOpener {
        LlamaCppGgufSession open(
                Path modelPath,
                LlamaCppGgufSessionParameters parameters,
                LlamaCppGgufLibraryPaths libraryPaths) throws Exception;
    }

    private static final class CachedSession {
        private final LlamaCppGgufSession session;
        private int activeLeases;
        private boolean retired;
        private boolean sessionClosed;

        private CachedSession(LlamaCppGgufSession session) {
            this.session = session;
        }
    }

    public static final class SessionLease implements AutoCloseable {
        private final LlamaCppGgufSession session;
        private final boolean reused;
        private final boolean closeOnClose;
        private final String reuseKind;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        public SessionLease(LlamaCppGgufSession session, boolean reused,
                boolean closeOnClose, String reuseKind) {
            this(session, reused, closeOnClose, reuseKind, null);
        }

        private SessionLease(LlamaCppGgufSession session, boolean reused,
                boolean closeOnClose, String reuseKind, Runnable release) {
            this.session = session;
            this.reused = reused;
            this.closeOnClose = closeOnClose;
            this.reuseKind = reuseKind;
            this.release = release;
        }

        public LlamaCppGgufSession session() { return session; }
        public boolean reused() { return reused; }
        public boolean closeOnClose() { return closeOnClose; }
        public String reuseKind() { return reuseKind; }

        public static SessionLease open(Path modelPath,
                LlamaCppGgufSessionParameters parameters,
                LlamaCppGgufLibraryPaths libraryPaths) throws Exception {
            return new SessionLease(
                    LlamaCppGgufSession.open(modelPath, parameters, libraryPaths),
                    false, true, "cold");
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (release != null) {
                release.run();
            } else if (closeOnClose) {
                session.close();
            }
        }
    }

    private final Map<SessionKey, CachedSession> sessions = new LinkedHashMap<>(4, 0.75f, true);
    private final Map<Path, GgufJavaBackendSession> javaBackends =
            new LinkedHashMap<>(2, 0.75f, true);

    public synchronized GgufJavaBackendSession acquireJavaBackend(
            Path modelPath) throws Exception {
        ensureOpen();
        Path key = modelPath.toAbsolutePath().normalize();
        GgufJavaBackendSession existing = javaBackends.get(key);
        if (existing != null) {
            log.accept("java backend cache hit: " + key.getFileName());
            return existing;
        }
        log.accept("java backend cache miss: " + key.getFileName());
        GgufJavaBackendSession opened =
                tech.kayys.aqli.inference.gguf.GgufJavaBackendSession.open(key);
        javaBackends.put(key, opened);
        return opened;
    }

    public synchronized SessionLease acquire(
            Path modelPath,
            LlamaCppGgufSessionParameters parameters) throws Exception {
        ensureOpen();
        SessionKey key = new SessionKey(modelPath.toAbsolutePath().normalize(), parameters);
        LlamaCppGgufSession existing = sessions.get(key);
        if (existing != null) {
            log.accept("session cache hit: " + key.modelPath().getFileName()
                    + ", context=" + parameters.context()
                    + ", gpuLayers=" + parameters.gpuLayers()
                    + ", sessions=" + sessions.size());
            return lease(existing, true, "exact");
        }
        SessionLease compatible = findCompatible(key);
        if (compatible != null) {
            return compatible;
        }
        log.accept("session cache miss: " + key.modelPath().getFileName()
                + ", context=" + parameters.context()
                + ", gpuLayers=" + parameters.gpuLayers()
                + ", sessions=" + sessions.size());
        LlamaCppGgufSession opened =
                sessionOpener.open(key.modelPath(), parameters, libraryPaths);
        CachedSession cached = new CachedSession(opened);
        sessions.put(key, cached);
        SessionLease lease = lease(cached, false, "cold");
        evictOverflowSessions();
        return lease;
    }

    private SessionLease findCompatible(SessionKey requested) {
        List<SessionKey> compatibleKeys = new ArrayList<>();
        for (SessionKey candidate : sessions.keySet()) {
            if (isCompatibleWarmSession(candidate, requested)) {
                compatibleKeys.add(candidate);
            }
        }
        SessionKey selected = selectCompatibleSession(compatibleKeys);
        if (selected == null) {
            return null;
        }
        CachedSession cached = sessions.get(selected);
        LlamaCppGgufSession session = cached.session;
        log.accept("session cache compatible hit: " + selected.modelPath().getFileName()
                + ", requestedContext=" + requested.parameters().context()
                + ", warmContext=" + selected.parameters().context()
                + ", requestedBatch=" + requested.parameters().batch()
                + ", warmBatch=" + selected.parameters().batch()
                + ", gpuLayers=" + selected.parameters().gpuLayers()
                + ", sessions=" + sessions.size());
        return lease(cached, true, "compatible");
    }

    static boolean isCompatibleWarmSession(SessionKey warm, SessionKey requested) {
        LlamaCppGgufSessionParameters warmParameters = warm.parameters();
        LlamaCppGgufSessionParameters requestedParameters = requested.parameters();
        return warm.modelPath().equals(requested.modelPath())
                && warmParameters.context() >= requestedParameters.context()
                && warmParameters.batch() >= requestedParameters.batch()
                && warmParameters.microBatch() >= requestedParameters.microBatch()
                && warmParameters.threads() == requestedParameters.threads()
                && warmParameters.batchThreads() == requestedParameters.batchThreads()
                && warmParameters.gpuLayers() == requestedParameters.gpuLayers()
                && warmParameters.useMmap() == requestedParameters.useMmap()
                && warmParameters.useMlock() == requestedParameters.useMlock()
                && warmParameters.swaFull() == requestedParameters.swaFull();
    }

    static SessionKey selectCompatibleSession(List<SessionKey> candidates) {
        SessionKey selected = null;
        for (SessionKey candidate : candidates) {
            boolean dominated = false;
            for (SessionKey other : candidates) {
                if (!candidate.equals(other) && lessOverprovisioned(other, candidate)) {
                    dominated = true;
                    break;
                }
            }
            // Candidates arrive in LRU-to-MRU order. Among equally non-dominated
            // capacity trade-offs, prefer the most recently used warm session.
            if (!dominated) {
                selected = candidate;
            }
        }
        return selected;
    }

    static boolean lessOverprovisioned(SessionKey left, SessionKey right) {
        LlamaCppGgufSessionParameters leftParameters = left.parameters();
        LlamaCppGgufSessionParameters rightParameters = right.parameters();
        boolean noLarger = leftParameters.context() <= rightParameters.context()
                && leftParameters.batch() <= rightParameters.batch()
                && leftParameters.microBatch() <= rightParameters.microBatch();
        boolean strictlySmaller = leftParameters.context() < rightParameters.context()
                || leftParameters.batch() < rightParameters.batch()
                || leftParameters.microBatch() < rightParameters.microBatch();
        return noLarger && strictlySmaller;
    }

    private void evictOverflowSessions() {
        int maxSessions = effectiveMaxSessions(maxSessionsSupplier.getAsInt());
        while (sessions.size() > maxSessions) {
            Map.Entry<SessionKey, CachedSession> eldest = sessions.entrySet().iterator().next();
            sessions.remove(eldest.getKey());
            log.accept("session cache evict: " + eldest.getKey().modelPath().getFileName()
                    + ", context=" + eldest.getKey().parameters().context()
                    + ", gpuLayers=" + eldest.getKey().parameters().gpuLayers()
                    + ", maxSessions=" + maxSessions);
            retire(eldest.getValue());
        }
    }

    static int effectiveMaxSessions(int configuredMaxSessions) {
        return Math.max(1, configuredMaxSessions);
    }

    private SessionLease lease(CachedSession cached, boolean reused, String reuseKind) {
        cached.activeLeases++;
        return new SessionLease(cached.session, reused, false, reuseKind, () -> release(cached));
    }

    private synchronized void release(CachedSession cached) {
        if (cached.activeLeases <= 0) {
            throw new IllegalStateException("GGUF session lease released more than once");
        }
        cached.activeLeases--;
        if (cached.retired && cached.activeLeases == 0) {
            closeRetired(cached);
        }
    }

    private void retire(CachedSession cached) {
        cached.retired = true;
        if (cached.activeLeases == 0) {
            closeRetired(cached);
        }
    }

    private static void closeRetired(CachedSession cached) {
        if (cached.sessionClosed) {
            return;
        }
        cached.sessionClosed = true;
        cached.session.close();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("GGUF session cache is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (CachedSession session : sessions.values()) {
            retire(session);
        }
        sessions.clear();
        for (tech.kayys.aqli.inference.gguf.GgufJavaBackendSession backendSession : javaBackends.values()) {
            try {
                backendSession.close();
            } catch (Exception ignored) {
            }
        }
        javaBackends.clear();
    }
}

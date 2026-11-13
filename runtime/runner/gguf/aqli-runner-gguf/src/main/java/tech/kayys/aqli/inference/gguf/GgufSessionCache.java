package tech.kayys.aqli.inference.gguf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private boolean closed;

    public GgufSessionCache(IntSupplier maxSessionsSupplier, Consumer<String> log,
            LlamaCppGgufLibraryPaths libraryPaths) {
        this.maxSessionsSupplier = maxSessionsSupplier;
        this.log = log;
        this.libraryPaths = libraryPaths;
    }

    record SessionKey(Path modelPath, LlamaCppGgufSessionParameters parameters) {
    }

    public record SessionLease(
        LlamaCppGgufSession session,
        boolean reused,
        boolean closeOnClose,
        String reuseKind) implements AutoCloseable {
    public static SessionLease open(
            Path modelPath,
            LlamaCppGgufSessionParameters parameters,
            LlamaCppGgufLibraryPaths libraryPaths) throws Exception {
        return new SessionLease(
                LlamaCppGgufSession.open(modelPath, parameters, libraryPaths),
                false,
                true,
                "cold");
    }

        @Override
    public void close() {
        if (closeOnClose) {
            session.close();
        }
    }
}

    private final Map<SessionKey, LlamaCppGgufSession> sessions = new LinkedHashMap<>(4, 0.75f, true);
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
            return new SessionLease(existing, true, false, "exact");
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
                LlamaCppGgufSession.open(key.modelPath(), parameters, libraryPaths);
        sessions.put(key, opened);
        evictOverflowSessions();
        return new SessionLease(opened, false, false, "cold");
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
        LlamaCppGgufSession session = sessions.get(selected);
        log.accept("session cache compatible hit: " + selected.modelPath().getFileName()
                + ", requestedContext=" + requested.parameters().context()
                + ", warmContext=" + selected.parameters().context()
                + ", requestedBatch=" + requested.parameters().batch()
                + ", warmBatch=" + selected.parameters().batch()
                + ", gpuLayers=" + selected.parameters().gpuLayers()
                + ", sessions=" + sessions.size());
        return new SessionLease(session, true, false, "compatible");
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
            Map.Entry<SessionKey, LlamaCppGgufSession> eldest = sessions.entrySet().iterator().next();
            sessions.remove(eldest.getKey());
            log.accept("session cache evict: " + eldest.getKey().modelPath().getFileName()
                    + ", context=" + eldest.getKey().parameters().context()
                    + ", gpuLayers=" + eldest.getKey().parameters().gpuLayers()
                    + ", maxSessions=" + maxSessions);
            eldest.getValue().close();
        }
    }

    static int effectiveMaxSessions(int configuredMaxSessions) {
        return Math.max(1, configuredMaxSessions);
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
        for (LlamaCppGgufSession session : sessions.values()) {
            session.close();
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

package tech.kayys.aqli.inference.gguf;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Owns daemon-scoped GGUF sessions and Java backend sessions. It centralizes
 * exact/compatible reuse, access-order eviction, one-shot leases, and shutdown.
 * Session limits, library paths, and logging are supplied by the application boundary.
 */
public final class GgufSessionCache implements AutoCloseable {
    private final IntSupplier maxSessionsSupplier;
    private final Consumer<String> log;
    private final LlamaCppGgufLibraryPaths libraryPaths;

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

    public SessionLease acquire(
            Path modelPath,
            LlamaCppGgufSessionParameters parameters) throws Exception {
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
        Map.Entry<SessionKey, LlamaCppGgufSession> best = null;
        for (Map.Entry<SessionKey, LlamaCppGgufSession> entry : sessions.entrySet()) {
            if (!isCompatibleWarmSession(entry.getKey(), requested)) {
                continue;
            }
            if (best == null || smallerCompatibleSession(entry.getKey(), best.getKey())) {
                best = entry;
            }
        }
        if (best == null) {
            return null;
        }
        SessionKey selected = best.getKey();
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

    static boolean smallerCompatibleSession(SessionKey left, SessionKey right) {
        LlamaCppGgufSessionParameters leftParameters = left.parameters();
        LlamaCppGgufSessionParameters rightParameters = right.parameters();
        int contextCompare = Integer.compare(leftParameters.context(), rightParameters.context());
        if (contextCompare != 0) {
            return contextCompare < 0;
        }
        int batchCompare = Integer.compare(leftParameters.batch(), rightParameters.batch());
        if (batchCompare != 0) {
            return batchCompare < 0;
        }
        return Integer.compare(leftParameters.microBatch(), rightParameters.microBatch()) < 0;
    }

    private void evictOverflowSessions() {
        int maxSessions = maxSessionsSupplier.getAsInt();
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

    @Override
    public void close() {
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

package tech.kayys.aqli.inference.gguf;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import tech.kayys.aljabr.gguf.api.GgufBackend;
import tech.kayys.aljabr.gguf.api.GgufBackendProvider;
import tech.kayys.aljabr.gguf.javabackend.JavaGgufBackendProvider;

/**
 * Owns or borrows a Java-native GGUF backend for a runner operation.
 *
 * <p>One-shot sessions own and close their backend. When retained by a daemon
 * cache, the cache owns this session and closes it during cache shutdown.</p>
 */
public final class GgufJavaBackendSession implements AutoCloseable {

    private final GgufBackend backend;
    private final boolean closeOnClose;
    private boolean closed;

    private GgufJavaBackendSession(GgufBackend backend, boolean closeOnClose) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.closeOnClose = closeOnClose;
    }

    /** Opens a backend whose lifecycle is owned by the returned session. */
    public static GgufJavaBackendSession open(Path modelPath) throws Exception {
        Objects.requireNonNull(modelPath, "modelPath");
        GgufBackendProvider provider = new JavaGgufBackendProvider();
        return owned(provider.create(modelPath.toAbsolutePath().normalize(), Map.of("backend", "java")));
    }

    static GgufJavaBackendSession owned(GgufBackend backend) {
        return new GgufJavaBackendSession(backend, true);
    }

    /**
     * Executes one request while holding the session monitor. Cached sessions may
     * be shared by concurrent callers, but the underlying backend is not assumed
     * to support overlapping executions on the same model instance.
     */
    public synchronized tech.kayys.aljabr.spi.inference.InferenceResponse execute(
            tech.kayys.aljabr.spi.inference.InferenceRequest request) throws Exception {
        if (closed) {
            throw new IllegalStateException("GGUF backend session is closed");
        }
        return backend.execute(Objects.requireNonNull(request, "request"));
    }

    /** Returns the backend while this session remains open. */
    public synchronized GgufBackend backend() {
        if (closed) {
            throw new IllegalStateException("GGUF backend session is closed");
        }
        return backend;
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        if (closeOnClose) {
            backend.close();
        }
    }
}

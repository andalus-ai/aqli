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
 * <p>One-shot sessions own and close their backend. Cached sessions are owned
 * by the cache and are exposed to individual operations as borrowed sessions.</p>
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
        return new GgufJavaBackendSession(
                provider.create(modelPath.toAbsolutePath().normalize(), Map.of("backend", "java")),
                true);
    }

    /** Wraps a backend whose lifecycle is managed by an external cache. */
    public static GgufJavaBackendSession borrowed(GgufBackend backend) {
        return new GgufJavaBackendSession(backend, false);
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

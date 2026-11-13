package tech.kayys.aqli.inference.gguf;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import tech.kayys.aljabr.gguf.api.GgufBackend;
import tech.kayys.aljabr.gguf.api.GgufBackendProvider;
import tech.kayys.aljabr.gguf.javabackend.JavaGgufBackendProvider;
import tech.kayys.aljabr.spi.inference.InferenceRequest;
import tech.kayys.aljabr.spi.inference.InferenceResponse;

/**
 * Owns a Java-native GGUF backend. Borrowers should use {@link Lease} so cache
 * retirement cannot close the backend between acquisition and execution.
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

    /** Creates a one-shot lease which closes its session when released. */
    public static Lease ownedLease(GgufJavaBackendSession session) {
        Objects.requireNonNull(session, "session");
        return new Lease(session, session::close);
    }

    static Lease borrowedLease(GgufJavaBackendSession session, Runnable release) {
        return new Lease(session, release);
    }

    /**
     * Executes one request while holding the session monitor. Concurrent
     * borrowers of one model session are serialized.
     */
    public synchronized InferenceResponse execute(InferenceRequest request) throws Exception {
        if (closed) {
            throw new IllegalStateException("GGUF backend session is closed");
        }
        return backend.execute(Objects.requireNonNull(request, "request"));
    }

    /**
     * Returns the underlying backend while this session remains open.
     * Direct backend calls bypass execution serialization; prefer {@link #execute(InferenceRequest)}.
     */
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

    /** A single borrow of a backend session; closing it releases that borrow. */
    public static final class Lease implements AutoCloseable {
        private final GgufJavaBackendSession session;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(GgufJavaBackendSession session, Runnable release) {
            this.session = Objects.requireNonNull(session, "session");
            this.release = Objects.requireNonNull(release, "release");
        }

        public InferenceResponse execute(InferenceRequest request) throws Exception {
            if (closed.get()) {
                throw new IllegalStateException("GGUF backend lease is closed");
            }
            return session.execute(request);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}

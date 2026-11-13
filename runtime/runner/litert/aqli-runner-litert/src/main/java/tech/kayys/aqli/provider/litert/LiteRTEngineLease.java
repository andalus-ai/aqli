package tech.kayys.aqli.provider.litert;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A scoped view of a LiteRT engine owned either by a direct invocation or an engine cache.
 *
 * <p>Borrowed leases share engine resources and prewarm progress with their owner, but closing
 * a borrowed lease never closes the shared engine.</p>
 */
public final class LiteRTEngineLease<T extends AutoCloseable> implements AutoCloseable {
    private final T engine;
    private final String backendName;
    private final boolean speculativeDecoding;
    private final String fallbackReason;
    private final boolean closeOnClose;
    private final boolean reused;
    private final SharedResources sharedResources;

    public LiteRTEngineLease(
            T engine,
            String backendName,
            boolean speculativeDecoding,
            String fallbackReason,
            boolean closeOnClose,
            boolean reused) {
        this(engine, backendName, speculativeDecoding, fallbackReason, closeOnClose, reused,
                new SharedResources());
    }

    private LiteRTEngineLease(
            T engine,
            String backendName,
            boolean speculativeDecoding,
            String fallbackReason,
            boolean closeOnClose,
            boolean reused,
            SharedResources sharedResources) {
        this.engine = engine;
        this.backendName = backendName;
        this.speculativeDecoding = speculativeDecoding;
        this.fallbackReason = fallbackReason;
        this.closeOnClose = closeOnClose;
        this.reused = reused;
        this.sharedResources = sharedResources;
    }

    public T engine() {
        return engine;
    }

    public String backendName() {
        return backendName;
    }

    public boolean speculativeDecoding() {
        return speculativeDecoding;
    }

    public String fallbackReason() {
        return fallbackReason;
    }

    public boolean reused() {
        return reused;
    }

    public LiteRTEngineLease<T> borrowed(boolean reused) {
        return new LiteRTEngineLease<>(
                engine, backendName, speculativeDecoding, fallbackReason, false, reused, sharedResources);
    }

    /**
     * Attaches an optional long-lived resource whose lifetime is coupled to the cached engine.
     * The CLI uses this for its daemon keep-alive conversation.
     */
    public void attachKeepAlive(AutoCloseable resource) {
        sharedResources.attachKeepAlive(resource);
    }

    public int remainingPrewarmIterations(int targetIterations) {
        return sharedResources.remainingIterations(targetIterations);
    }

    public void markPrewarmed(int completedIterations) {
        sharedResources.markCompleted(completedIterations);
    }

    @Override
    public void close() {
        if (closeOnClose) {
            sharedResources.close(engine);
        }
    }

    public void forceClose() {
        sharedResources.close(engine);
    }

    private static final class SharedResources {
        private final AtomicBoolean closed = new AtomicBoolean();
        private int prewarmIterationsCompleted;
        private AutoCloseable keepAlive;

        synchronized void attachKeepAlive(AutoCloseable resource) {
            if (resource == null) {
                return;
            }
            if (closed.get()) {
                closeQuietly(resource);
                return;
            }
            if (keepAlive != null) {
                closeQuietly(resource);
                throw new IllegalStateException("A LiteRT engine lease already has a keep-alive resource");
            }
            keepAlive = resource;
        }

        synchronized int remainingIterations(int targetIterations) {
            return Math.max(0, targetIterations - prewarmIterationsCompleted);
        }

        synchronized void markCompleted(int completedIterations) {
            prewarmIterationsCompleted = Math.max(prewarmIterationsCompleted, completedIterations);
        }

        void close(AutoCloseable engine) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            AutoCloseable keepAliveToClose;
            synchronized (this) {
                keepAliveToClose = keepAlive;
                keepAlive = null;
            }
            closeQuietly(keepAliveToClose);
            closeQuietly(engine);
        }

        private static void closeQuietly(AutoCloseable resource) {
            if (resource == null) {
                return;
            }
            try {
                resource.close();
            } catch (Throwable ignored) {
                // Cleanup must not mask the original inference or shutdown result.
            }
        }
    }
}

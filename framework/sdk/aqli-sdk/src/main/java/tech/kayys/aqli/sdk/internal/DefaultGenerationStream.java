package tech.kayys.aqli.sdk.internal;

import tech.kayys.aqli.sdk.model.GenerationStream;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory bridge from an inference publisher to the listener-based SDK stream.
 *
 * <p>Cancellation is propagated to the upstream subscription when it is attached.
 * Terminal completion and failure release the cancellation action without invoking it.
 */
public class DefaultGenerationStream implements GenerationStream {

    private final List<Consumer<String>> tokenListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> completeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Throwable>> errorListeners = new CopyOnWriteArrayList<>();
    private final Object stateLock = new Object();

    private boolean cancelled;
    private boolean terminated;
    private Runnable cancellationAction;

    public void emitToken(String token) {
        synchronized (stateLock) {
            if (cancelled || terminated) {
                return;
            }
            tokenListeners.forEach(listener -> listener.accept(token));
        }
    }

    public void emitComplete() {
        synchronized (stateLock) {
            if (cancelled || terminated) {
                return;
            }
            terminated = true;
            cancellationAction = null;
            completeListeners.forEach(Runnable::run);
        }
    }

    public void emitError(Throwable error) {
        synchronized (stateLock) {
            if (cancelled || terminated) {
                return;
            }
            terminated = true;
            cancellationAction = null;
            errorListeners.forEach(listener -> listener.accept(error));
        }
    }

    /**
     * Attaches the upstream cancellation action. Safe when the upstream terminates
     * synchronously or cancellation wins the race before attachment.
     */
    void attachCancellation(Runnable action) {
        boolean cancelImmediately;
        synchronized (stateLock) {
            cancelImmediately = cancelled;
            if (!cancelled && !terminated) {
                cancellationAction = action;
            }
        }
        if (cancelImmediately) {
            action.run();
        }
    }

    @Override
    public GenerationStream onToken(Consumer<String> tokenListener) {
        tokenListeners.add(tokenListener);
        return this;
    }

    @Override
    public GenerationStream onComplete(Runnable completeListener) {
        completeListeners.add(completeListener);
        return this;
    }

    @Override
    public GenerationStream onError(Consumer<Throwable> errorListener) {
        errorListeners.add(errorListener);
        return this;
    }

    @Override
    public void cancel() {
        Runnable action;
        synchronized (stateLock) {
            if (cancelled || terminated) {
                return;
            }
            cancelled = true;
            action = cancellationAction;
            cancellationAction = null;
        }
        if (action != null) {
            action.run();
        }
    }
}

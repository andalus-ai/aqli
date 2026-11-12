package tech.kayys.aqli.cli.commands.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Coordinates the lifecycle of a CLI streaming inference attempt.
 *
 * <p>The coordinator owns subscription, the optional single fallback attempt, terminal
 * signaling, and interruption handling. It deliberately knows nothing about terminal
 * formatting, audio resources, metrics, or output publication; those remain callbacks
 * supplied by the command adapter.</p>
 */
public final class RunStreamExecutionCoordinator {

    public Result execute(
            Multi<StreamingInferenceChunk> primary,
            Supplier<Multi<StreamingInferenceChunk>> fallbackStream,
            BooleanSupplier fallbackAvailable,
            Consumer<StreamingInferenceChunk> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onFailure,
            Runnable onInterrupted,
            Consumer<Throwable> onFallback) {
        Objects.requireNonNull(fallbackStream, "fallbackStream");
        Objects.requireNonNull(fallbackAvailable, "fallbackAvailable");
        Objects.requireNonNull(onChunk, "onChunk");
        Objects.requireNonNull(onComplete, "onComplete");
        Objects.requireNonNull(onFailure, "onFailure");
        Objects.requireNonNull(onInterrupted, "onInterrupted");
        Objects.requireNonNull(onFallback, "onFallback");

        Session session = new Session(
                fallbackStream, fallbackAvailable, onChunk, onComplete,
                onFailure, onInterrupted, onFallback);
        session.subscribe(primary, false);
        return session.await();
    }

    public record Result(boolean failed) {
    }

    private static final class Session {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean failed = new AtomicBoolean();
        private final AtomicBoolean fallbackAttempted = new AtomicBoolean();
        private final ConcurrentLinkedQueue<Cancellable> subscriptions = new ConcurrentLinkedQueue<>();
        private final Supplier<Multi<StreamingInferenceChunk>> fallbackStream;
        private final BooleanSupplier fallbackAvailable;
        private final Consumer<StreamingInferenceChunk> onChunk;
        private final Runnable onComplete;
        private final Consumer<Throwable> onFailure;
        private final Runnable onInterrupted;
        private final Consumer<Throwable> onFallback;

        private Session(
                Supplier<Multi<StreamingInferenceChunk>> fallbackStream,
                BooleanSupplier fallbackAvailable,
                Consumer<StreamingInferenceChunk> onChunk,
                Runnable onComplete,
                Consumer<Throwable> onFailure,
                Runnable onInterrupted,
                Consumer<Throwable> onFallback) {
            this.fallbackStream = fallbackStream;
            this.fallbackAvailable = fallbackAvailable;
            this.onChunk = onChunk;
            this.onComplete = onComplete;
            this.onFailure = onFailure;
            this.onInterrupted = onInterrupted;
            this.onFallback = onFallback;
        }

        private void subscribe(Multi<StreamingInferenceChunk> stream, boolean fallback) {
            if (stream == null) {
                fail(new IllegalStateException("Streaming inference returned no stream"));
                return;
            }
            Cancellable current = stream.subscribe().with(
                    chunk -> {
                        if (!terminal.get()) {
                            try {
                                onChunk.accept(chunk);
                            } catch (Throwable chunkFailure) {
                                fail(chunkFailure);
                            }
                        }
                    },
                    error -> {
                        if (!fallback && canAttemptFallback()) {
                            try {
                                onFallback.accept(error);
                                subscribe(fallbackStream.get(), true);
                            } catch (Throwable fallbackStartFailure) {
                                fail(fallbackStartFailure);
                            }
                        } else {
                            fail(error);
                        }
                    },
                    this::succeed);
            subscriptions.add(current);
            if (terminal.get()) {
                current.cancel();
            }
        }

        private boolean canAttemptFallback() {
            if (fallbackAttempted.get()) {
                return false;
            }
            boolean available;
            try {
                available = fallbackAvailable.getAsBoolean();
            } catch (Throwable ignored) {
                available = false;
            }
            return available && fallbackAttempted.compareAndSet(false, true);
        }

        private void succeed() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            try {
                onComplete.run();
            } catch (Throwable completionFailure) {
                failed.set(true);
                try {
                    onFailure.accept(completionFailure);
                } catch (Throwable ignored) {
                    // Preserve the original completion failure as the execution result.
                }
            } finally {
                completed.countDown();
            }
        }

        private void fail(Throwable error) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            failed.set(true);
            cancelSubscription();
            try {
                onFailure.accept(error);
            } catch (Throwable ignored) {
                // Failure presentation must not prevent the command from terminating.
            } finally {
                completed.countDown();
            }
        }

        private void cancelSubscription() {
            Cancellable current;
            while ((current = subscriptions.poll()) != null) {
                current.cancel();
            }
        }

        private Result await() {
            try {
                completed.await();
            } catch (InterruptedException interrupted) {
                failed.set(true);
                if (terminal.compareAndSet(false, true)) {
                    cancelSubscription();
                    try {
                        onInterrupted.run();
                    } catch (Throwable ignored) {
                        // Preserve interruption and return a failed result.
                    }
                }
                Thread.currentThread().interrupt();
            }
            return new Result(failed.get());
        }
    }
}

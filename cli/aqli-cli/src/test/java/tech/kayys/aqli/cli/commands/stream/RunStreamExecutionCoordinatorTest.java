package tech.kayys.aqli.cli.commands.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.smallrye.mutiny.Multi;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;

class RunStreamExecutionCoordinatorTest {

    private final RunStreamExecutionCoordinator coordinator = new RunStreamExecutionCoordinator();

    @Test
    void completesPrimaryStreamAndInvokesChunkAndCompletionCallbacks() {
        AtomicInteger chunks = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        var result = coordinator.execute(
                Multi.createFrom().item(mock(StreamingInferenceChunk.class)),
                () -> { throw new AssertionError("fallback must not be requested"); },
                () -> false,
                chunk -> chunks.incrementAndGet(),
                completed::incrementAndGet,
                error -> { throw new AssertionError(error); },
                () -> { },
                error -> { throw new AssertionError(error); });

        assertFalse(result.failed());
        assertEquals(1, chunks.get());
        assertEquals(1, completed.get());
    }

    @Test
    void reportsPrimaryFailureWhenFallbackIsUnavailable() {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        var result = coordinator.execute(
                Multi.<StreamingInferenceChunk>createFrom().failure(new IllegalStateException("primary failed")),
                () -> { throw new AssertionError("fallback must not be requested"); },
                () -> false,
                chunk -> { },
                () -> { throw new AssertionError("failed stream cannot complete"); },
                failure::set,
                () -> { },
                error -> { });

        assertTrue(result.failed());
        assertEquals("primary failed", failure.get().getMessage());
    }

    @Test
    void attemptsFallbackOnceAndCompletesFromFallbackStream() {
        AtomicInteger fallbackRequests = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        var result = coordinator.execute(
                Multi.<StreamingInferenceChunk>createFrom().failure(new IllegalStateException("primary failed")),
                () -> {
                    fallbackRequests.incrementAndGet();
                    return Multi.createFrom().item(mock(StreamingInferenceChunk.class));
                },
                () -> true,
                chunk -> { },
                completed::incrementAndGet,
                error -> { throw new AssertionError(error); },
                () -> { },
                error -> { });

        assertFalse(result.failed());
        assertEquals(1, fallbackRequests.get());
        assertEquals(1, completed.get());
    }

    @Test
    void reportsFallbackFailureAndDoesNotRetry() {
        AtomicInteger fallbackRequests = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        var result = coordinator.execute(
                Multi.<StreamingInferenceChunk>createFrom().failure(new IllegalStateException("primary failed")),
                () -> {
                    fallbackRequests.incrementAndGet();
                    return Multi.<StreamingInferenceChunk>createFrom()
                            .failure(new IllegalStateException("fallback failed"));
                },
                () -> true,
                chunk -> { },
                () -> { throw new AssertionError("failed fallback cannot complete"); },
                failure::set,
                () -> { },
                error -> { });

        assertTrue(result.failed());
        assertEquals(1, fallbackRequests.get());
        assertEquals("fallback failed", failure.get().getMessage());
    }

    @Test
    void interruptedWaitClosesResourcesAndRestoresInterruptFlag() throws Exception {
        AtomicInteger interrupted = new AtomicInteger();
        AtomicReference<RunStreamExecutionCoordinator.Result> result = new AtomicReference<>();
        CountDownLatch subscribed = new CountDownLatch(1);
        Thread worker = new Thread(() -> result.set(coordinator.execute(
                Multi.<StreamingInferenceChunk>createFrom().emitter(emitter -> subscribed.countDown()),
                () -> Multi.<StreamingInferenceChunk>createFrom().nothing(),
                () -> false,
                chunk -> { },
                () -> { },
                error -> { },
                interrupted::incrementAndGet,
                error -> { })));

        worker.start();
        assertTrue(subscribed.await(2, TimeUnit.SECONDS), "stream should subscribe before interruption");
        worker.interrupt();
        worker.join(2_000);

        assertFalse(worker.isAlive());
        assertTrue(result.get().failed());
        assertTrue(worker.isInterrupted());
        assertEquals(1, interrupted.get());
    }
}

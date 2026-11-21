/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectStreamExecutionTest {
    private static final Logger LOG = Logger.getLogger(DirectStreamExecutionTest.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void runsWorkOnConfiguredWorkerAndCompletes() {
        AtomicBoolean cleanupCalled = new AtomicBoolean(false);
        AtomicReference<String> workerName = new AtomicReference<>();
        ThreadFactory threadFactory = runnable -> new Thread(runnable, "direct-stream-test");

        List<Integer> items = DirectStreamExecution.<Integer>create(
                threadFactory,
                LOG,
                "stream failed",
                () -> cleanupCalled.set(true),
                emitter -> {
                    workerName.set(Thread.currentThread().getName());
                    emitter.emit(1);
                    emitter.emit(2);
                })
                .collect().asList().await().atMost(TIMEOUT);

        assertEquals(List.of(1, 2), items);
        assertEquals("direct-stream-test", workerName.get());
        assertTrue(cleanupCalled.get());
    }

    @Test
    void cancellationInterruptsActiveWorkerAndRunsCleanup() throws Exception {
        AtomicBoolean cleanupCalled = new AtomicBoolean(false);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);

        var subscription = DirectStreamExecution.<Integer>create(
                runnable -> new Thread(runnable, "direct-stream-cancellable"),
                LOG,
                "stream failed",
                () -> cleanupCalled.set(true),
                emitter -> {
                    started.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException expected) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                })
                .subscribe().with(item -> { });

        assertTrue(started.await(2, TimeUnit.SECONDS));
        subscription.cancel();

        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!cleanupCalled.get() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(interrupted.get(), "cancellation should interrupt the generation worker");
        assertTrue(cleanupCalled.get(), "worker cleanup should run after cancellation");
    }

    @Test
    void propagatesFailureAndStillRunsCleanup() {
        AtomicBoolean cleanupCalled = new AtomicBoolean(false);
        IllegalStateException failure = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> DirectStreamExecution.<Integer>create(
                        runnable -> new Thread(runnable, "direct-stream-test-failure"),
                        LOG,
                        "stream failed",
                        () -> cleanupCalled.set(true),
                        emitter -> {
                            throw failure;
                        })
                        .collect().asList().await().atMost(TIMEOUT));

        assertSame(failure, thrown);
        assertTrue(cleanupCalled.get());
    }
}

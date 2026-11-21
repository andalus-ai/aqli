/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectForwardCancellationTest {

    @Test
    void checkpointReturnsWhenWorkerIsNotInterrupted() {
        assertDoesNotThrow(DirectForwardCancellation::checkpoint);
    }

    @Test
    void cacheCommitRunsOnlyWhenWorkerIsActive() {
        AtomicInteger commits = new AtomicInteger();
        DirectForwardCancellation.commitIfActive(commits::incrementAndGet);
        assertTrue(commits.get() == 1);
    }

    @Test
    void cancelledWorkerCannotCommitCacheAdvance() {
        AtomicInteger commits = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class,
                    () -> DirectForwardCancellation.commitIfActive(commits::incrementAndGet));
            assertTrue(commits.get() == 0,
                    "cancelled forward execution must not advance committed KV cache position");
            assertTrue(Thread.currentThread().isInterrupted(),
                    "commit guard must preserve the interrupt signal");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void checkpointThrowsWithoutClearingInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, DirectForwardCancellation::checkpoint);
            assertTrue(Thread.currentThread().isInterrupted(),
                    "checkpoint must not consume the worker interrupt signal");
        } finally {
            Thread.interrupted();
        }
        assertFalse(Thread.currentThread().isInterrupted());
    }
}

/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

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

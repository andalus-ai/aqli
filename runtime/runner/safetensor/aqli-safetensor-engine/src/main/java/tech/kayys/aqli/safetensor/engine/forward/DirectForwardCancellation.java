/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.forward;

import java.util.concurrent.CancellationException;

/**
 * Cooperative cancellation checkpoints for CPU-side direct forward execution.
 *
 * <p>The streaming owner interrupts its worker when the subscriber cancels.
 * Forward execution checks that interrupt at safe boundaries; native kernels
 * that do not respond to interruption remain best-effort until they return.
 */
final class DirectForwardCancellation {
    private DirectForwardCancellation() {
    }

    static void checkpoint() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Direct SafeTensor inference was cancelled");
        }
    }

    /** Runs a state-committing action only while the worker remains active. */
    static void commitIfActive(Runnable commit) {
        if (commit == null) {
            throw new IllegalArgumentException("commit action is required");
        }
        checkpoint();
        commit.run();
    }
}

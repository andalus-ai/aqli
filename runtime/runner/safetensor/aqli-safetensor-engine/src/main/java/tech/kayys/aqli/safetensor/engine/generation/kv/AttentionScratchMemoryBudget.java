/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.kv;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide admission budget for temporary native attention scratch buffers.
 *
 * <p>Unlike the KV and forward-workspace budgets, reservations are short-lived
 * and released when the dispatch-owned arena closes.
 */
@ApplicationScoped
public class AttentionScratchMemoryBudget {
    private final long maxBytes;
    private long reservedBytes;

    @Inject
    public AttentionScratchMemoryBudget(
            @ConfigProperty(name = "aqli.safetensor.attention-scratch.max-native-bytes",
                    defaultValue = "2147483648") long maxBytes) {
        this(maxBytes, true);
    }

    private AttentionScratchMemoryBudget(long maxBytes, boolean validate) {
        if (validate && maxBytes <= 0) {
            throw new IllegalArgumentException("Attention scratch native memory budget must be positive");
        }
        this.maxBytes = maxBytes;
    }

    static AttentionScratchMemoryBudget unbounded() {
        return new AttentionScratchMemoryBudget(Long.MAX_VALUE, false);
    }

    public synchronized Lease reserve(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("Attention scratch reservation must be positive");
        }
        final long next;
        try {
            next = Math.addExact(reservedBytes, bytes);
        } catch (ArithmeticException overflow) {
            throw exceeded(bytes);
        }
        if (next > maxBytes) {
            throw exceeded(bytes);
        }
        reservedBytes = next;
        return new Lease(this, bytes);
    }

    public synchronized long reservedBytes() {
        return reservedBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }

    private IllegalStateException exceeded(long requestedBytes) {
        return new IllegalStateException(
                "Attention scratch native memory budget exceeded: requested " + requestedBytes
                        + " bytes, reserved " + reservedBytes + " of " + maxBytes + " bytes");
    }

    private synchronized void release(long bytes) {
        if (bytes > reservedBytes) {
            throw new IllegalStateException("Attention scratch release exceeds outstanding reservation");
        }
        reservedBytes -= bytes;
    }

    public static final class Lease implements AutoCloseable {
        private final AttentionScratchMemoryBudget owner;
        private final long bytes;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(AttentionScratchMemoryBudget owner, long bytes) {
            this.owner = owner;
            this.bytes = bytes;
        }

        public long bytes() {
            return bytes;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.release(bytes);
            }
        }
    }
}

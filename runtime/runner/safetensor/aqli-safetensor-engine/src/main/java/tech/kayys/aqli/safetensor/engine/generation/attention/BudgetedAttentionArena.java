/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.attention;

import tech.kayys.aqli.safetensor.engine.generation.kv.AttentionScratchMemoryBudget;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dispatch-scoped arena that reserves native bytes before each allocation.
 *
 * <p>All reservations remain held until the arena is closed, matching the
 * lifetime of the segments even when a dispatch tries more than one kernel.
 */
final class BudgetedAttentionArena implements AutoCloseable {
    private final Arena arena = Arena.ofConfined();
    private final AttentionScratchMemoryBudget budget;
    private final List<AttentionScratchMemoryBudget.Lease> leases = new ArrayList<>();
    private boolean closed;

    BudgetedAttentionArena(AttentionScratchMemoryBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    synchronized MemorySegment allocate(long bytes, long alignment) {
        if (closed) {
            throw new IllegalStateException("Attention scratch arena is closed");
        }
        if (bytes <= 0 || alignment <= 0) {
            throw new IllegalArgumentException("Attention scratch allocation size and alignment must be positive");
        }
        AttentionScratchMemoryBudget.Lease lease = budget.reserve(bytes);
        try {
            MemorySegment segment = arena.allocate(bytes, alignment);
            leases.add(lease);
            return segment;
        } catch (RuntimeException | Error failure) {
            try {
                lease.close();
            } catch (RuntimeException | Error releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }

    synchronized long reservedBytes() {
        long total = 0;
        for (AttentionScratchMemoryBudget.Lease lease : leases) {
            total = Math.addExact(total, lease.bytes());
        }
        return total;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = null;
        try {
            arena.close();
        } catch (RuntimeException | Error closeFailure) {
            failure = closeFailure;
        } finally {
            for (int i = leases.size() - 1; i >= 0; i--) {
                try {
                    leases.get(i).close();
                } catch (RuntimeException | Error releaseFailure) {
                    if (failure == null) {
                        failure = releaseFailure;
                    } else if (failure != releaseFailure) {
                        failure.addSuppressed(releaseFailure);
                    }
                }
            }
            leases.clear();
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}

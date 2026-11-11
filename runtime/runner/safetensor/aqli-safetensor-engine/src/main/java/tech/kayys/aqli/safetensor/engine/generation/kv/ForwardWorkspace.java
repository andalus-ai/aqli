/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.kv;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reusable scratch memory for direct forward execution.
 *
 * <p>The workspace is owned by a generation session, but kept separate from KV
 * cache block management so attention/cache code does not also own forward
 * projection, logits, and metadata scratch allocation policy.
 *
 * <p>Each arena is admitted independently. Growth is transactional: the old
 * arena remains live and budgeted until its replacement has been allocated.
 */
public class ForwardWorkspace implements AutoCloseable {
    private final ForwardWorkspaceMemoryBudget memoryBudget;
    private final AtomicBoolean closed = new AtomicBoolean();

    private MemorySegment normedAttnSeg;
    private MemorySegment normedFfnSeg;
    private MemorySegment gateSeg;
    private MemorySegment upSeg;
    private MemorySegment combinedSeg;
    private MemorySegment hiddenASeg;
    private MemorySegment hiddenBSeg;
    private MemorySegment logitsSeg;
    private Arena hiddenArena;
    private Arena combinedArena;
    private Arena projectionArena;
    private Arena logitsArena;
    private Arena attentionMetadataArena;
    private ForwardWorkspaceMemoryBudget.Lease hiddenLease;
    private ForwardWorkspaceMemoryBudget.Lease combinedLease;
    private ForwardWorkspaceMemoryBudget.Lease projectionLease;
    private ForwardWorkspaceMemoryBudget.Lease logitsLease;
    private ForwardWorkspaceMemoryBudget.Lease attentionMetadataLease;
    private MemorySegment attentionBlockTableSeg;
    private MemorySegment attentionContextLensSeg;
    private long hiddenCap;
    private long combinedCap;
    private long projectionCap;
    private long logitsCap;
    private long attentionBlockTableCap;
    private long attentionContextLensCap;

    /** Supports standalone tests and callers that do not share a managed runtime budget. */
    public ForwardWorkspace() {
        this(ForwardWorkspaceMemoryBudget.unbounded());
    }

    public ForwardWorkspace(ForwardWorkspaceMemoryBudget memoryBudget) {
        this.memoryBudget = java.util.Objects.requireNonNull(memoryBudget, "memoryBudget");
    }

    public synchronized void ensureCapacity(long totalElements, long hiddenSize, long intermediateSize) {
        ensureOpen();
        if (totalElements <= 0 || hiddenSize <= 0 || intermediateSize <= 0) {
            throw new IllegalArgumentException("Forward workspace dimensions must be positive");
        }
        long needed = Math.multiplyExact(Math.max(hiddenSize, totalElements), Float.BYTES);
        long paddedNeeded = align4096(needed);
        long tokenSlots = totalElements / hiddenSize;
        long combinedElements = Math.max(intermediateSize, Math.multiplyExact(tokenSlots, intermediateSize));
        long paddedCombined = align4096(Math.multiplyExact(combinedElements, Float.BYTES));

        if (normedAttnSeg == null || hiddenCap < paddedNeeded) {
            ArenaAllocation allocation = allocateArena(paddedNeeded, 4, 4096);
            Arena previousArena = hiddenArena;
            ForwardWorkspaceMemoryBudget.Lease previousLease = hiddenLease;
            hiddenArena = allocation.arena();
            hiddenLease = allocation.lease();
            normedAttnSeg = allocation.segments()[0];
            normedFfnSeg = allocation.segments()[1];
            hiddenASeg = allocation.segments()[2];
            hiddenBSeg = allocation.segments()[3];
            hiddenCap = paddedNeeded;
            release(previousArena, previousLease);
        }
        if (combinedSeg == null || combinedCap < paddedCombined) {
            ArenaAllocation allocation = allocateArena(paddedCombined, 1, 4096);
            Arena previousArena = combinedArena;
            ForwardWorkspaceMemoryBudget.Lease previousLease = combinedLease;
            combinedArena = allocation.arena();
            combinedLease = allocation.lease();
            combinedSeg = allocation.segments()[0];
            combinedCap = paddedCombined;
            release(previousArena, previousLease);
        }
    }

    public synchronized void ensureLogitsCapacity(long requiredElements) {
        ensureOpen();
        long needed = Math.multiplyExact(Math.max(1L, requiredElements), Float.BYTES);
        long padded = align4096(needed);
        if (logitsSeg == null || logitsCap < padded) {
            ArenaAllocation allocation = allocateArena(padded, 1, 4096);
            Arena previousArena = logitsArena;
            ForwardWorkspaceMemoryBudget.Lease previousLease = logitsLease;
            logitsArena = allocation.arena();
            logitsLease = allocation.lease();
            logitsSeg = allocation.segments()[0];
            logitsCap = padded;
            release(previousArena, previousLease);
        }
    }

    public synchronized void ensureProjectionScratchCapacity(long requiredBytes) {
        ensureOpen();
        long padded = align4096(Math.max(1L, requiredBytes));
        if (gateSeg == null || projectionCap < padded) {
            ArenaAllocation allocation = allocateArena(padded, 2, 4096);
            Arena previousArena = projectionArena;
            ForwardWorkspaceMemoryBudget.Lease previousLease = projectionLease;
            projectionArena = allocation.arena();
            projectionLease = allocation.lease();
            gateSeg = allocation.segments()[0];
            upSeg = allocation.segments()[1];
            projectionCap = padded;
            release(previousArena, previousLease);
        }
    }

    public synchronized void ensureAttentionMetadataCapacity(int blockTableEntries, int batch) {
        ensureOpen();
        long blockTableBytes = Math.multiplyExact((long) Math.max(1, blockTableEntries), Integer.BYTES);
        long contextLensBytes = Math.multiplyExact((long) Math.max(1, batch), Integer.BYTES);
        if (attentionMetadataArena == null
                || attentionBlockTableCap < blockTableBytes
                || attentionContextLensCap < contextLensBytes) {
            ArenaAllocation allocation = allocateArena(
                    new long[] { blockTableBytes, contextLensBytes },
                    new long[] { Integer.BYTES, Integer.BYTES });
            Arena previousArena = attentionMetadataArena;
            ForwardWorkspaceMemoryBudget.Lease previousLease = attentionMetadataLease;
            attentionMetadataArena = allocation.arena();
            attentionMetadataLease = allocation.lease();
            attentionBlockTableSeg = allocation.segments()[0];
            attentionContextLensSeg = allocation.segments()[1];
            attentionBlockTableCap = blockTableBytes;
            attentionContextLensCap = contextLensBytes;
            release(previousArena, previousLease);
        }
    }

    public synchronized MemorySegment getNormedAttnSeg() {
        ensureOpen();
        return normedAttnSeg;
    }

    public synchronized MemorySegment getNormedFfnSeg() {
        ensureOpen();
        return normedFfnSeg;
    }

    public synchronized MemorySegment getGateSeg() {
        ensureOpen();
        return gateSeg;
    }

    public synchronized MemorySegment getUpSeg() {
        ensureOpen();
        return upSeg;
    }

    public synchronized MemorySegment getCombinedSeg() {
        ensureOpen();
        return combinedSeg;
    }

    public synchronized MemorySegment getHiddenASeg() {
        ensureOpen();
        return hiddenASeg;
    }

    public synchronized MemorySegment getHiddenBSeg() {
        ensureOpen();
        return hiddenBSeg;
    }

    public synchronized MemorySegment getLogitsSeg() {
        ensureOpen();
        return logitsSeg;
    }

    public synchronized MemorySegment getAttentionBlockTableSeg() {
        ensureOpen();
        return attentionBlockTableSeg;
    }

    public synchronized MemorySegment getAttentionContextLensSeg() {
        ensureOpen();
        return attentionContextLensSeg;
    }

    public synchronized long allocatedNativeBytes() {
        ensureOpen();
        long total = 0;
        total = Math.addExact(total, leaseBytes(hiddenLease));
        total = Math.addExact(total, leaseBytes(combinedLease));
        total = Math.addExact(total, leaseBytes(projectionLease));
        total = Math.addExact(total, leaseBytes(logitsLease));
        return Math.addExact(total, leaseBytes(attentionMetadataLease));
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Throwable failure = null;
        failure = release(hiddenArena, hiddenLease, failure);
        failure = release(combinedArena, combinedLease, failure);
        failure = release(projectionArena, projectionLease, failure);
        failure = release(logitsArena, logitsLease, failure);
        failure = release(attentionMetadataArena, attentionMetadataLease, failure);
        hiddenArena = null;
        combinedArena = null;
        projectionArena = null;
        logitsArena = null;
        attentionMetadataArena = null;
        hiddenLease = null;
        combinedLease = null;
        projectionLease = null;
        logitsLease = null;
        attentionMetadataLease = null;
        normedAttnSeg = null;
        normedFfnSeg = null;
        gateSeg = null;
        upSeg = null;
        combinedSeg = null;
        hiddenASeg = null;
        hiddenBSeg = null;
        logitsSeg = null;
        attentionBlockTableSeg = null;
        attentionContextLensSeg = null;
        hiddenCap = 0;
        combinedCap = 0;
        projectionCap = 0;
        logitsCap = 0;
        attentionBlockTableCap = 0;
        attentionContextLensCap = 0;
        rethrow(failure);
    }

    private ArenaAllocation allocateArena(long bytesPerSegment, int segmentCount, long alignment) {
        long[] sizes = new long[segmentCount];
        long[] alignments = new long[segmentCount];
        java.util.Arrays.fill(sizes, bytesPerSegment);
        java.util.Arrays.fill(alignments, alignment);
        return allocateArena(sizes, alignments);
    }

    private ArenaAllocation allocateArena(long[] sizes, long[] alignments) {
        if (sizes.length == 0 || sizes.length != alignments.length) {
            throw new IllegalArgumentException("Invalid forward workspace allocation layout");
        }
        long totalBytes = 0;
        for (long size : sizes) {
            if (size <= 0) {
                throw new IllegalArgumentException("Forward workspace allocations must be positive");
            }
            totalBytes = Math.addExact(totalBytes, size);
        }
        ForwardWorkspaceMemoryBudget.Lease lease = memoryBudget.reserve(totalBytes);
        Arena arena = null;
        try {
            arena = Arena.ofShared();
            MemorySegment[] segments = new MemorySegment[sizes.length];
            for (int i = 0; i < sizes.length; i++) {
                segments[i] = arena.allocate(sizes[i], alignments[i]);
            }
            return new ArenaAllocation(arena, lease, segments);
        } catch (RuntimeException | Error failure) {
            if (arena != null) {
                try {
                    arena.close();
                } catch (RuntimeException | Error closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            try {
                lease.close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static long align4096(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Workspace byte size must not be negative");
        }
        return Math.addExact(bytes, 4095L) & ~4095L;
    }

    private static long leaseBytes(ForwardWorkspaceMemoryBudget.Lease lease) {
        return lease == null ? 0 : lease.bytes();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Forward workspace is closed");
        }
    }

    private static void release(Arena arena, ForwardWorkspaceMemoryBudget.Lease lease) {
        Throwable failure = release(arena, lease, null);
        rethrow(failure);
    }

    private static Throwable release(
            Arena arena, ForwardWorkspaceMemoryBudget.Lease lease, Throwable failure) {
        try {
            if (arena != null) {
                arena.close();
            }
        } catch (RuntimeException | Error closeFailure) {
            failure = combine(failure, closeFailure);
        } finally {
            try {
                if (lease != null) {
                    lease.close();
                }
            } catch (RuntimeException | Error releaseFailure) {
                failure = combine(failure, releaseFailure);
            }
        }
        return failure;
    }

    private static Throwable combine(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        if (primary != additional) {
            primary.addSuppressed(additional);
        }
        return primary;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException("Forward workspace cleanup failed", failure);
        }
    }

    private record ArenaAllocation(
            Arena arena,
            ForwardWorkspaceMemoryBudget.Lease lease,
            MemorySegment[] segments) {
    }
}

/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.kv;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Objects;

/**
 * Owns the physical K/V storage for one generation session.
 *
 * <p>Physical memory is allocated lazily in chunks as block indices are leased.
 * Each chunk reserves native-memory budget before allocation and releases that
 * reservation when its final block is returned.
 */
public final class BlockManager implements AutoCloseable {
    private static final int BLOCKS_PER_CHUNK = 16;

    public enum KvStorageType {
        FP32,
        INT8,
        INT4
    }

    private final KvCacheMemoryBudget memoryBudget;
    private int blockSizeBytes;
    private int tokensPerBlock;
    private int numHeads;
    private int headDim;
    private long headStride;
    private long tokenStride;
    private long scaleStride;
    private long scaleBlockBytes;
    private boolean initialized;
    private int maxBlocks;
    private KvStorageType storageType = KvStorageType.FP32;
    private int bytesPerElement = Float.BYTES;
    private StorageChunk[] chunks;
    private long allocatedNativeBytes;
    private final ArrayDeque<Integer> freeBlockIndices = new ArrayDeque<>();
    private final BitSet allocatedBlocks = new BitSet();

    public BlockManager() {
        this(KvCacheMemoryBudget.unbounded());
    }

    BlockManager(KvCacheMemoryBudget memoryBudget) {
        this.memoryBudget = Objects.requireNonNull(memoryBudget, "memoryBudget");
    }

    public synchronized void initialize(
            int tokensPerBlock, int numHeads, int headDim, int maxBlocks) {
        initialize(tokensPerBlock, numHeads, headDim, maxBlocks, KvStorageType.FP32);
    }

    public synchronized void initialize(
            int tokensPerBlock, int numHeads, int headDim, int maxBlocks, KvStorageType storageType) {
        validateDimensions(tokensPerBlock, numHeads, headDim, maxBlocks);
        Objects.requireNonNull(storageType, "storageType");

        if (initialized
                && this.tokensPerBlock == tokensPerBlock
                && this.numHeads == numHeads
                && this.headDim == headDim
                && this.storageType == storageType
                && this.maxBlocks == maxBlocks) {
            return;
        }
        if (initialized && !allocatedBlocks.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot reinitialize KV storage while " + allocatedBlocks.cardinality() + " blocks are leased");
        }

        long blockElements = Math.multiplyExact((long) numHeads,
                Math.multiplyExact((long) tokensPerBlock, headDim));
        long blockBytes = switch (storageType) {
            case FP32 -> Math.multiplyExact(blockElements, Float.BYTES);
            case INT8 -> blockElements;
            case INT4 -> Math.addExact(blockElements, 1L) / 2L;
        };
        int newBlockSizeBytes = Math.toIntExact(blockBytes);
        long newScaleBlockBytes = storageType == KvStorageType.FP32
                ? 0L
                : Math.multiplyExact(Math.multiplyExact((long) numHeads, tokensPerBlock), Float.BYTES);

        closeChunks();
        this.tokensPerBlock = tokensPerBlock;
        this.numHeads = numHeads;
        this.headDim = headDim;
        this.maxBlocks = maxBlocks;
        this.storageType = storageType;
        this.bytesPerElement = storageType == KvStorageType.FP32 ? Float.BYTES : Byte.BYTES;
        this.blockSizeBytes = newBlockSizeBytes;
        this.tokenStride = headDim;
        this.headStride = Math.multiplyExact((long) tokensPerBlock, tokenStride);
        this.scaleStride = tokensPerBlock;
        this.scaleBlockBytes = newScaleBlockBytes;
        this.chunks = new StorageChunk[(int) (((long) maxBlocks + BLOCKS_PER_CHUNK - 1) / BLOCKS_PER_CHUNK)];
        this.initialized = true;
        resetFreeList();
    }

    private static void validateDimensions(int tokensPerBlock, int numHeads, int headDim, int maxBlocks) {
        if (tokensPerBlock <= 0 || numHeads <= 0 || headDim <= 0 || maxBlocks <= 0) {
            throw new IllegalArgumentException("KV pool dimensions and capacity must all be positive"
                    + " (tokensPerBlock=" + tokensPerBlock
                    + ", numHeads=" + numHeads
                    + ", headDim=" + headDim
                    + ", maxBlocks=" + maxBlocks + ")");
        }
    }

    private void resetFreeList() {
        freeBlockIndices.clear();
        allocatedBlocks.clear();
        for (int i = 0; i < maxBlocks; i++) {
            freeBlockIndices.addLast(i);
        }
    }

    public synchronized Integer allocateBlock() {
        ensureInitialized();
        Integer index = freeBlockIndices.pollFirst();
        if (index == null) {
            return null;
        }
        try {
            ensureChunkAllocated(index);
        } catch (RuntimeException | Error failure) {
            freeBlockIndices.addFirst(index);
            throw failure;
        }
        allocatedBlocks.set(index);
        chunks[chunkIndex(index)].allocatedBlockCount++;
        return index;
    }

    public synchronized void freeBlock(int index) {
        ensureInitialized();
        checkBlockIndex(index);
        if (!allocatedBlocks.get(index)) {
            throw new IllegalStateException("KV block " + index + " is not currently allocated");
        }
        int chunkIndex = chunkIndex(index);
        StorageChunk chunk = chunks[chunkIndex];
        allocatedBlocks.clear(index);
        freeBlockIndices.addLast(index);
        chunk.allocatedBlockCount--;
        if (chunk.allocatedBlockCount == 0) {
            releaseChunk(chunkIndex);
        }
    }

    public synchronized MemorySegment getKBlock(int blockIndex) {
        ensureInitialized();
        checkBlockIndex(blockIndex);
        StorageChunk chunk = requireChunk(blockIndex);
        long localOffset = (long) (blockIndex - chunk.firstBlock) * blockSizeBytes;
        return chunk.kPool.asSlice(localOffset, blockSizeBytes);
    }

    public synchronized MemorySegment getVBlock(int blockIndex) {
        ensureInitialized();
        checkBlockIndex(blockIndex);
        StorageChunk chunk = requireChunk(blockIndex);
        long localOffset = (long) (blockIndex - chunk.firstBlock) * blockSizeBytes;
        return chunk.vPool.asSlice(localOffset, blockSizeBytes);
    }

    /**
     * Returns a contiguous pool only when the entire configured capacity fits
     * in one physical chunk. Multi-chunk callers must use block-level access.
     */
    @Deprecated(forRemoval = false)
    public synchronized MemorySegment getRawKPool() {
        ensureInitialized();
        return requireSingleChunkPool().kPool;
    }

    @Deprecated(forRemoval = false)
    public synchronized MemorySegment getRawVPool() {
        ensureInitialized();
        return requireSingleChunkPool().vPool;
    }

    private StorageChunk requireSingleChunkPool() {
        if (chunks.length != 1 || chunks[0] == null || chunks[0].blockCount != maxBlocks) {
            throw new UnsupportedOperationException(
                    "A contiguous KV pool is unavailable for chunked storage; use getKBlock/getVBlock");
        }
        return chunks[0];
    }

    public synchronized int getBlockSizeBytes() {
        ensureInitialized();
        return blockSizeBytes;
    }

    public synchronized int getTokensPerBlock() {
        ensureInitialized();
        return tokensPerBlock;
    }

    public synchronized long getHeadStride() {
        ensureInitialized();
        return headStride;
    }

    public synchronized long getTokenStride() {
        ensureInitialized();
        return tokenStride;
    }

    public synchronized long getScaleStride() {
        ensureInitialized();
        return scaleStride;
    }

    public synchronized KvStorageType getStorageType() {
        ensureInitialized();
        return storageType;
    }

    public synchronized boolean isInt8Quantized() {
        ensureInitialized();
        return storageType == KvStorageType.INT8;
    }

    public synchronized boolean isInt4Quantized() {
        ensureInitialized();
        return storageType == KvStorageType.INT4;
    }

    public synchronized boolean isQuantized() {
        ensureInitialized();
        return storageType != KvStorageType.FP32;
    }

    public synchronized int getBytesPerElement() {
        ensureInitialized();
        return bytesPerElement;
    }

    public synchronized MemorySegment getKScaleBlock(int blockIndex) {
        ensureInitialized();
        checkBlockIndex(blockIndex);
        if (scaleBlockBytes == 0) {
            return null;
        }
        StorageChunk chunk = requireChunk(blockIndex);
        long localOffset = (long) (blockIndex - chunk.firstBlock) * scaleBlockBytes;
        return chunk.kScalePool.asSlice(localOffset, scaleBlockBytes);
    }

    public synchronized MemorySegment getVScaleBlock(int blockIndex) {
        ensureInitialized();
        checkBlockIndex(blockIndex);
        if (scaleBlockBytes == 0) {
            return null;
        }
        StorageChunk chunk = requireChunk(blockIndex);
        long localOffset = (long) (blockIndex - chunk.firstBlock) * scaleBlockBytes;
        return chunk.vScalePool.asSlice(localOffset, scaleBlockBytes);
    }

    public synchronized int getAllocatedBlockCount() {
        return allocatedBlocks.cardinality();
    }

    public synchronized int getAllocatedChunkCount() {
        int count = 0;
        if (chunks != null) {
            for (StorageChunk chunk : chunks) {
                if (chunk != null) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Current native bytes allocated by physical chunks. */
    public synchronized long getAllocatedNativeBytes() {
        return allocatedNativeBytes;
    }

    public synchronized int getCapacity() {
        ensureInitialized();
        return maxBlocks;
    }

    private void ensureChunkAllocated(int blockIndex) {
        int chunkIndex = chunkIndex(blockIndex);
        if (chunks[chunkIndex] != null) {
            return;
        }

        int firstBlock = chunkIndex * BLOCKS_PER_CHUNK;
        int blockCount = Math.min(BLOCKS_PER_CHUNK, maxBlocks - firstBlock);
        long dataBytes = Math.multiplyExact((long) blockCount, blockSizeBytes);
        long scaleBytes = Math.multiplyExact((long) blockCount, scaleBlockBytes);
        long totalBytes = Math.addExact(Math.multiplyExact(dataBytes, 2L),
                Math.multiplyExact(scaleBytes, 2L));
        long nextAllocatedBytes = Math.addExact(allocatedNativeBytes, totalBytes);
        KvCacheMemoryBudget.Lease memoryLease = memoryBudget.reserve(totalBytes);
        Arena arena = null;
        try {
            arena = Arena.ofShared();
            MemorySegment kPool = arena.allocate(dataBytes, 64);
            MemorySegment vPool = arena.allocate(dataBytes, 64);
            MemorySegment kScalePool = scaleBlockBytes == 0 ? null : arena.allocate(scaleBytes, 64);
            MemorySegment vScalePool = scaleBlockBytes == 0 ? null : arena.allocate(scaleBytes, 64);
            chunks[chunkIndex] = new StorageChunk(
                    firstBlock, blockCount, arena, kPool, vPool, kScalePool, vScalePool,
                    totalBytes, memoryLease);
            allocatedNativeBytes = nextAllocatedBytes;
        } catch (RuntimeException | Error failure) {
            if (arena != null) {
                try {
                    arena.close();
                } catch (RuntimeException | Error closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            memoryLease.close();
            throw failure;
        }
    }

    private StorageChunk requireChunk(int blockIndex) {
        StorageChunk chunk = chunks[chunkIndex(blockIndex)];
        if (chunk == null) {
            throw new IllegalStateException("KV block " + blockIndex + " has no allocated storage chunk");
        }
        return chunk;
    }

    private int chunkIndex(int blockIndex) {
        return blockIndex / BLOCKS_PER_CHUNK;
    }

    private void releaseChunk(int chunkIndex) {
        StorageChunk chunk = chunks[chunkIndex];
        if (chunk == null) {
            return;
        }
        try {
            chunk.arena.close();
        } finally {
            chunks[chunkIndex] = null;
            allocatedNativeBytes -= chunk.allocatedBytes;
            chunk.memoryLease.close();
        }
    }

    private void closeChunks() {
        if (chunks != null) {
            for (int i = 0; i < chunks.length; i++) {
                releaseChunk(i);
            }
        }
        allocatedNativeBytes = 0;
    }

    private void checkBlockIndex(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= maxBlocks) {
            throw new IndexOutOfBoundsException(
                    "KV block index " + blockIndex + " outside [0, " + maxBlocks + ")");
        }
    }

    private void ensureInitialized() {
        if (!initialized || chunks == null) {
            throw new IllegalStateException("KV block storage is not initialized");
        }
    }

    @Override
    public synchronized void close() {
        closeChunks();
        chunks = null;
        freeBlockIndices.clear();
        allocatedBlocks.clear();
        initialized = false;
        maxBlocks = 0;
        blockSizeBytes = 0;
        tokensPerBlock = 0;
        numHeads = 0;
        headDim = 0;
        headStride = 0;
        tokenStride = 0;
        scaleStride = 0;
        scaleBlockBytes = 0;
        bytesPerElement = Float.BYTES;
        storageType = KvStorageType.FP32;
    }

    private static final class StorageChunk {
        private final int firstBlock;
        private final int blockCount;
        private final Arena arena;
        private final MemorySegment kPool;
        private final MemorySegment vPool;
        private final MemorySegment kScalePool;
        private final MemorySegment vScalePool;
        private final long allocatedBytes;
        private final KvCacheMemoryBudget.Lease memoryLease;
        private int allocatedBlockCount;

        private StorageChunk(
                int firstBlock,
                int blockCount,
                Arena arena,
                MemorySegment kPool,
                MemorySegment vPool,
                MemorySegment kScalePool,
                MemorySegment vScalePool,
                long allocatedBytes,
                KvCacheMemoryBudget.Lease memoryLease) {
            this.firstBlock = firstBlock;
            this.blockCount = blockCount;
            this.arena = arena;
            this.kPool = kPool;
            this.vPool = vPool;
            this.kScalePool = kScalePool;
            this.vScalePool = vScalePool;
            this.allocatedBytes = allocatedBytes;
            this.memoryLease = memoryLease;
        }
    }
}

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
 * <p>The pool is deliberately session-scoped. A block index is leased at most
 * once at a time, and the arena must outlive every segment view obtained from
 * this manager.
 */
public final class BlockManager implements AutoCloseable {
    public enum KvStorageType {
        FP32,
        INT8,
        INT4
    }

    private MemorySegment kPoolSlab;
    private MemorySegment vPoolSlab;
    private MemorySegment kScaleSlab;
    private MemorySegment vScaleSlab;
    private int blockSizeBytes;
    private int tokensPerBlock;
    private int numHeads;
    private int headDim;
    private long headStride;
    private long tokenStride;
    private long scaleStride;
    private boolean initialized;
    private int maxBlocks;
    private KvStorageType storageType = KvStorageType.FP32;
    private int bytesPerElement = Float.BYTES;
    private Arena poolArena;
    private final ArrayDeque<Integer> freeBlockIndices = new ArrayDeque<>();
    private final BitSet allocatedBlocks = new BitSet();

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
        long totalBytes = Math.multiplyExact((long) maxBlocks, blockBytes);
        long totalScaleBytes = storageType == KvStorageType.FP32
                ? 0L
                : Math.multiplyExact(Math.multiplyExact(
                        Math.multiplyExact((long) maxBlocks, numHeads), tokensPerBlock), Float.BYTES);

        Arena newArena = Arena.ofShared();
        MemorySegment newK;
        MemorySegment newV;
        MemorySegment newKScale = null;
        MemorySegment newVScale = null;
        try {
            newK = newArena.allocate(totalBytes, 64);
            newV = newArena.allocate(totalBytes, 64);
            if (storageType != KvStorageType.FP32) {
                newKScale = newArena.allocate(totalScaleBytes, 64);
                newVScale = newArena.allocate(totalScaleBytes, 64);
            }
        } catch (RuntimeException | Error failure) {
            newArena.close();
            throw failure;
        }

        closeArena();
        this.poolArena = newArena;
        this.kPoolSlab = newK;
        this.vPoolSlab = newV;
        this.kScaleSlab = newKScale;
        this.vScaleSlab = newVScale;
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
        if (index != null) {
            allocatedBlocks.set(index);
        }
        return index;
    }

    public synchronized void freeBlock(int index) {
        ensureInitialized();
        checkBlockIndex(index);
        if (!allocatedBlocks.get(index)) {
            throw new IllegalStateException("KV block " + index + " is not currently allocated");
        }
        allocatedBlocks.clear(index);
        freeBlockIndices.addLast(index);
    }

    public synchronized MemorySegment getKBlock(int blockIndex) {
        ensureInitialized();
        checkBlockIndex(blockIndex);
        return kPoolSlab.asSlice((long) blockIndex * blockSizeBytes, blockSizeBytes);
    }

    public synchronized MemorySegment getVBlock(int blockIndex) {
        ensureInitialized();
        checkBlockIndex(blockIndex);
        return vPoolSlab.asSlice((long) blockIndex * blockSizeBytes, blockSizeBytes);
    }

    public synchronized MemorySegment getRawKPool() {
        ensureInitialized();
        return kPoolSlab;
    }

    public synchronized MemorySegment getRawVPool() {
        ensureInitialized();
        return vPoolSlab;
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
        if (kScaleSlab == null) {
            return null;
        }
        long scaleBlockBytes = (long) numHeads * tokensPerBlock * Float.BYTES;
        return kScaleSlab.asSlice((long) blockIndex * scaleBlockBytes, scaleBlockBytes);
    }

    public synchronized MemorySegment getVScaleBlock(int blockIndex) {
        ensureInitialized();
        checkBlockIndex(blockIndex);
        if (vScaleSlab == null) {
            return null;
        }
        long scaleBlockBytes = (long) numHeads * tokensPerBlock * Float.BYTES;
        return vScaleSlab.asSlice((long) blockIndex * scaleBlockBytes, scaleBlockBytes);
    }

    public synchronized int getAllocatedBlockCount() {
        return allocatedBlocks.cardinality();
    }

    public synchronized int getCapacity() {
        ensureInitialized();
        return maxBlocks;
    }

    private void checkBlockIndex(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= maxBlocks) {
            throw new IndexOutOfBoundsException(
                    "KV block index " + blockIndex + " outside [0, " + maxBlocks + ")");
        }
    }

    private void ensureInitialized() {
        if (!initialized || poolArena == null || !poolArena.scope().isAlive()) {
            throw new IllegalStateException("KV block storage is not initialized");
        }
    }

    private void closeArena() {
        if (poolArena != null && poolArena.scope().isAlive()) {
            poolArena.close();
        }
    }

    @Override
    public synchronized void close() {
        closeArena();
        poolArena = null;
        kPoolSlab = null;
        vPoolSlab = null;
        kScaleSlab = null;
        vScaleSlab = null;
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
        bytesPerElement = Float.BYTES;
        storageType = KvStorageType.FP32;
    }
}

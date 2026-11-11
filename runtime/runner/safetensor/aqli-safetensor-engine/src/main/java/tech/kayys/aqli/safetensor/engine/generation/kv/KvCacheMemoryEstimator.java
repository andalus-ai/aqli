/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.kv;

/**
 * Calculates the maximum native bytes required if the configured KV block capacity is fully allocated.
 *
 * <p>The estimate mirrors {@link BlockManager}: K and V each have chunked block storage,
 * and quantized storage adds FP32 scale storage for each of K and V. It includes
 * the session's per-layer block capacity and fixed allocator margin. Actual
 * budget reservations are acquired incrementally as chunks are allocated.
 */
public final class KvCacheMemoryEstimator {
    public static final int DEFAULT_EXTRA_BLOCKS = 64;

    private KvCacheMemoryEstimator() {
    }

    public static Estimate estimate(
            int maxTokens,
            int numLayers,
            int numHeads,
            int headDim,
            int tokensPerBlock,
            BlockManager.KvStorageType storageType,
            int extraBlocks) {
        if (maxTokens <= 0 || numLayers <= 0 || numHeads <= 0 || headDim <= 0
                || tokensPerBlock <= 0 || extraBlocks < 0) {
            throw new IllegalArgumentException("KV memory dimensions must be positive and extraBlocks non-negative");
        }
        if (storageType == null) {
            throw new NullPointerException("storageType");
        }

        long blocksPerLayer = Math.addExact((long) maxTokens, tokensPerBlock - 1L) / tokensPerBlock;
        long blockCount = Math.addExact(
                Math.multiplyExact(blocksPerLayer, numLayers), extraBlocks);
        long elementsPerBlock = Math.multiplyExact(
                (long) tokensPerBlock, Math.multiplyExact((long) numHeads, headDim));
        long dataBytesPerBlock = switch (storageType) {
            case FP32 -> Math.multiplyExact(elementsPerBlock, Float.BYTES);
            case INT8 -> elementsPerBlock;
            case INT4 -> Math.addExact(elementsPerBlock, 1L) / 2L;
        };
        long dataSlabBytes = Math.multiplyExact(
                Math.multiplyExact(blockCount, dataBytesPerBlock), 2L);
        long scaleSlabBytes = storageType == BlockManager.KvStorageType.FP32
                ? 0L
                : Math.multiplyExact(
                        Math.multiplyExact(
                                Math.multiplyExact(blockCount, numHeads),
                                tokensPerBlock),
                        2L * Float.BYTES);
        return new Estimate(blockCount, dataSlabBytes, scaleSlabBytes,
                Math.addExact(dataSlabBytes, scaleSlabBytes));
    }

    public record Estimate(
            long blockCount,
            long dataSlabBytes,
            long scaleSlabBytes,
            long totalBytes) {
    }
}

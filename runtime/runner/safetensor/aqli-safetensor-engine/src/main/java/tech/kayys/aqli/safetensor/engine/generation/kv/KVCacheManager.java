/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.kv;

import tech.kayys.aqli.safetensor.engine.generation.attention.SharedKvState;
import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;
import tech.kayys.aljabr.spi.model.ModelConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * FFM-backed KV (key-value) cache manager using {@link AccelTensor}.
 *
 * <p>No LibTorch dependency — pure MemorySegment storage.
 */
@ApplicationScoped
public class KVCacheManager {
    private final KvCacheMemoryBudget memoryBudget;
    private final ForwardWorkspaceMemoryBudget workspaceMemoryBudget;

    /** Supports direct construction in focused unit tests; CDI uses the budgeted constructor. */
    public KVCacheManager() {
        this(KvCacheMemoryBudget.unbounded(), ForwardWorkspaceMemoryBudget.unbounded());
    }

    /** Compatibility constructor for tests and direct embedders. */
    public KVCacheManager(KvCacheMemoryBudget memoryBudget) {
        this(memoryBudget, ForwardWorkspaceMemoryBudget.unbounded());
    }

    @Inject
    public KVCacheManager(
            KvCacheMemoryBudget memoryBudget,
            ForwardWorkspaceMemoryBudget workspaceMemoryBudget) {
        this.memoryBudget = Objects.requireNonNull(memoryBudget, "memoryBudget");
        this.workspaceMemoryBudget = Objects.requireNonNull(workspaceMemoryBudget, "workspaceMemoryBudget");
    }

    public KVCacheSession createSession(int maxSeqLen) {
        return new KVCacheSession(
                maxSeqLen, new BlockManager(memoryBudget), memoryBudget, workspaceMemoryBudget);
    }

    public static class KVCacheSession implements AutoCloseable {
        private enum State {
            NEW,
            ALLOCATED,
            CLOSED
        }

        private static final int MAX_PREALLOCATED_TOKENS = 4096;

        private final int maxSeqLen;
        private final int maxSupportedTokens;
        private final BlockManager blockManager;
        private final KvCacheMemoryBudget memoryBudget;
        private final ForwardWorkspaceMemoryBudget workspaceMemoryBudget;
        private final Map<Integer, List<Integer>> blockTables = new java.util.concurrent.ConcurrentHashMap<>();

        private State state = State.NEW;
        private int currentPos;
        private int tokensPerBlock = 16;
        private int numLayers;
        private int numKVHeads;
        private int headDim;
        private ForwardWorkspace workspace;
        private final Map<Integer, SharedKvState> sharedKvStates =
                new java.util.concurrent.ConcurrentHashMap<>();
        private tech.kayys.aljabr.safetensor.generation.GenerationConfig.KvCacheQuantization kvQuantization =
                tech.kayys.aljabr.safetensor.generation.GenerationConfig.KvCacheQuantization.NONE;
        private Object mambaState;

        public KVCacheSession(int maxSeqLen, BlockManager blockManager) {
            this(maxSeqLen, Objects.requireNonNull(blockManager, "blockManager"),
                    blockManager.memoryBudget(), ForwardWorkspaceMemoryBudget.unbounded());
        }

        KVCacheSession(int maxSeqLen, BlockManager blockManager, KvCacheMemoryBudget memoryBudget) {
            this(maxSeqLen, blockManager, memoryBudget, ForwardWorkspaceMemoryBudget.unbounded());
        }

        KVCacheSession(
                int maxSeqLen,
                BlockManager blockManager,
                KvCacheMemoryBudget memoryBudget,
                ForwardWorkspaceMemoryBudget workspaceMemoryBudget) {
            if (maxSeqLen <= 0) {
                throw new IllegalArgumentException("maxSeqLen must be positive");
            }
            this.maxSeqLen = maxSeqLen;
            this.maxSupportedTokens = Math.min(maxSeqLen, MAX_PREALLOCATED_TOKENS);
            this.blockManager = Objects.requireNonNull(blockManager, "blockManager");
            this.memoryBudget = Objects.requireNonNull(memoryBudget, "memoryBudget");
            this.workspaceMemoryBudget = Objects.requireNonNull(workspaceMemoryBudget, "workspaceMemoryBudget");
            if (this.blockManager.memoryBudget() != this.memoryBudget) {
                throw new IllegalArgumentException(
                        "BlockManager and KVCacheSession must share the same native-memory budget");
            }
        }

        /**
         * Allocates the session's KV storage and forward workspace exactly once.
         * A failed allocation closes the session and releases every resource acquired
         * up to the failure point.
         */
        public synchronized void allocate(
                ModelConfig config,
                tech.kayys.aljabr.safetensor.generation.GenerationConfig genConfig) {
            requireState(State.NEW, "allocate");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(genConfig, "genConfig");

            try {
                this.numLayers = config.numHiddenLayers();
                this.numKVHeads = config.resolvedMaxKvHeads();
                this.headDim = config.resolvedMaxHeadDim();
                this.tokensPerBlock = 16;
                this.kvQuantization = genConfig.kvCacheQuant();

                BlockManager.KvStorageType storageType =
                        switch (kvQuantization) {
                            case INT8 -> BlockManager.KvStorageType.INT8;
                            case INT4, TURBO -> BlockManager.KvStorageType.INT4;
                            case NONE -> BlockManager.KvStorageType.FP32;
                        };

                // Keep the configured maximum capacity bounded. Physical storage is
                // materialized lazily and budgeted chunk by chunk as blocks are leased.
                KvCacheMemoryEstimator.Estimate estimate = KvCacheMemoryEstimator.estimate(
                        maxSupportedTokens, numLayers, numKVHeads, headDim, tokensPerBlock,
                        storageType, KvCacheMemoryEstimator.DEFAULT_EXTRA_BLOCKS);
                int initialBlocks = Math.toIntExact(estimate.blockCount());

                // The estimator validates maximum supported capacity. Physical
                // native memory is reserved incrementally as BlockManager allocates chunks.
                blockManager.initialize(tokensPerBlock, numKVHeads, headDim, initialBlocks, storageType);

                for (int layer = 0; layer < numLayers; layer++) {
                    blockTables.put(layer, new java.util.ArrayList<>());
                    appendBlock(layer);
                }

                workspace = new ForwardWorkspace(workspaceMemoryBudget);
                workspace.ensureCapacity(config.hiddenSize(), config.hiddenSize(), config.intermediateSize());
                state = State.ALLOCATED;
            } catch (RuntimeException | Error failure) {
                state = State.CLOSED;
                rethrow(cleanup(failure));
            }
        }

        public synchronized ForwardWorkspace getWorkspace() {
            requireAllocated("getWorkspace");
            return workspace;
        }

        public synchronized Object getMambaState() {
            ensureNotClosed("getMambaState");
            return mambaState;
        }

        public synchronized void setMambaState(Object mambaState) {
            ensureNotClosed("setMambaState");
            this.mambaState = mambaState;
        }

        private void appendBlock(int layerIdx) {
            Integer block = blockManager.allocateBlock();
            if (block == null) {
                throw new IllegalStateException("KVCache: Out of physical blocks!");
            }
            blockTables.get(layerIdx).add(block);
        }

        private void growBlockTables(int requiredBlocks) {
            List<BlockLease> acquired = new ArrayList<>();
            try {
                for (int layer = 0; layer < numLayers; layer++) {
                    List<Integer> table = blockTables.get(layer);
                    while (table.size() < requiredBlocks) {
                        Integer block = blockManager.allocateBlock();
                        if (block == null) {
                            throw new IllegalStateException("KVCache: Out of physical blocks!");
                        }
                        table.add(block);
                        acquired.add(new BlockLease(layer, block));
                    }
                }
            } catch (RuntimeException | Error failure) {
                for (int i = acquired.size() - 1; i >= 0; i--) {
                    BlockLease lease = acquired.get(i);
                    blockTables.get(lease.layer()).remove(Integer.valueOf(lease.block()));
                    try {
                        blockManager.freeBlock(lease.block());
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }

        private void requireWithinCapacity(int requestedTokens) {
            if (requestedTokens > maxSupportedTokens) {
                throw new IllegalArgumentException(
                        "Requested KV capacity " + requestedTokens + " exceeds session limit "
                                + maxSupportedTokens + " tokens (configured maxSeqLen=" + maxSeqLen
                                + ", current pool limit=" + MAX_PREALLOCATED_TOKENS + ")");
            }
        }

        private record BlockLease(int layer, int block) {
        }

        public synchronized List<Integer> getBlockTable(int layerIdx) {
            ensureNotClosed("getBlockTable");
            return blockTables.get(layerIdx);
        }

        /** Proactively allocates enough blocks for the requested token count. */
        public synchronized void ensureCapacity(int totalTokensNeeded) {
            requireAllocated("ensureCapacity");
            if (totalTokensNeeded <= 0) {
                return;
            }

            requireWithinCapacity(totalTokensNeeded);
            long required = ((long) totalTokensNeeded + tokensPerBlock - 1) / tokensPerBlock;
            int requiredBlocks = Math.toIntExact(required);
            growBlockTables(requiredBlocks);
        }

        public synchronized void advance(int seqLen) {
            requireAllocated("advance");
            if (seqLen < 0) {
                throw new IllegalArgumentException("seqLen must not be negative");
            }

            int nextPosition = Math.addExact(currentPos, seqLen);
            requireWithinCapacity(nextPosition);
            ensureCapacity(nextPosition);
            currentPos = nextPosition;
        }

        public synchronized int currentPos() {
            requireAllocated("currentPos");
            return currentPos;
        }

        public synchronized int tokensPerBlock() {
            requireAllocated("tokensPerBlock");
            return tokensPerBlock;
        }

        public synchronized int headDim() {
            requireAllocated("headDim");
            return headDim;
        }

        public synchronized int numKVHeads() {
            requireAllocated("numKVHeads");
            return numKVHeads;
        }

        @Deprecated(forRemoval = false)
        public synchronized java.lang.foreign.MemorySegment getRawKPool() {
            requireAllocated("getRawKPool");
            return blockManager.getRawKPool();
        }

        @Deprecated(forRemoval = false)
        public synchronized java.lang.foreign.MemorySegment getRawVPool() {
            requireAllocated("getRawVPool");
            return blockManager.getRawVPool();
        }

        public synchronized int getBlockForToken(int layerIdx, int tokenIdx) {
            requireAllocated("getBlockForToken");
            List<Integer> table = blockTables.get(layerIdx);
            if (table == null || tokenIdx < 0) {
                return -1;
            }
            if (tokenIdx >= maxSupportedTokens) {
                throw new IllegalArgumentException(
                        "Token index " + tokenIdx + " exceeds KV session capacity " + maxSupportedTokens);
            }
            ensureCapacity(Math.addExact(tokenIdx, 1));
            int blockIndex = tokenIdx / tokensPerBlock;
            return table.get(blockIndex);
        }

        public synchronized List<Integer> getBlockIndices(int layerIdx) {
            ensureNotClosed("getBlockIndices");
            return blockTables.get(layerIdx);
        }

        public synchronized BlockManager blockManager() {
            ensureNotClosed("blockManager");
            return blockManager;
        }

        /**
         * Creates shared-layer K/V state under this session's process-wide native-memory budget.
         * Ownership of both input tensors transfers to the returned state, including on failure.
         */
        public synchronized SharedKvState createSharedKvState(AccelTensor key, AccelTensor value) {
            requireAllocated("createSharedKvState");
            return new SharedKvState(key, value, memoryBudget);
        }

        public synchronized Map<Integer, SharedKvState> sharedKvStates() {
            ensureNotClosed("sharedKvStates");
            return sharedKvStates;
        }

        public synchronized void clearSharedKvStates() {
            ensureNotClosed("clearSharedKvStates");
            closeSharedKvStates();
        }

        private void closeSharedKvStates() {
            Throwable failure = null;
            for (SharedKvState sharedState : sharedKvStates.values()) {
                if (sharedState != null) {
                    try {
                        sharedState.close();
                    } catch (Throwable closeFailure) {
                        failure = combine(failure, closeFailure);
                    }
                }
            }
            sharedKvStates.clear();
            if (failure != null) {
                rethrow(failure);
            }
        }

        public synchronized tech.kayys.aljabr.safetensor.generation.GenerationConfig.KvCacheQuantization
                kvQuantization() {
            requireAllocated("kvQuantization");
            return kvQuantization;
        }

        public synchronized boolean isQuantizedInt8() {
            requireAllocated("isQuantizedInt8");
            return kvQuantization
                    == tech.kayys.aljabr.safetensor.generation.GenerationConfig.KvCacheQuantization.INT8;
        }

        public synchronized boolean isQuantizedInt4() {
            requireAllocated("isQuantizedInt4");
            return kvQuantization
                            == tech.kayys.aljabr.safetensor.generation.GenerationConfig.KvCacheQuantization.INT4
                    || kvQuantization
                            == tech.kayys.aljabr.safetensor.generation.GenerationConfig.KvCacheQuantization.TURBO;
        }

        public synchronized boolean isQuantized() {
            requireAllocated("isQuantized");
            return kvQuantization
                    != tech.kayys.aljabr.safetensor.generation.GenerationConfig.KvCacheQuantization.NONE;
        }

        @Override
        public synchronized void close() {
            if (state == State.CLOSED) {
                return;
            }
            state = State.CLOSED;
            rethrow(cleanup(null));
        }

        private Throwable cleanup(Throwable failure) {
            if (workspace != null) {
                try {
                    workspace.close();
                } catch (Throwable closeFailure) {
                    failure = combine(failure, closeFailure);
                }
                workspace = null;
            }

            for (SharedKvState sharedState : sharedKvStates.values()) {
                if (sharedState != null) {
                    try {
                        sharedState.close();
                    } catch (Throwable closeFailure) {
                        failure = combine(failure, closeFailure);
                    }
                }
            }
            sharedKvStates.clear();
            blockTables.clear();
            mambaState = null;

            try {
                // The block manager is session-owned; closing it releases all leases
                // and invalidates every MemorySegment view backed by its arena.
                blockManager.close();
            } catch (Throwable closeFailure) {
                failure = combine(failure, closeFailure);
            }

            return failure;
        }

        private void requireAllocated(String operation) {
            requireState(State.ALLOCATED, operation);
        }

        private void ensureNotClosed(String operation) {
            if (state == State.CLOSED) {
                throw new IllegalStateException("Cannot " + operation + " on a closed KV cache session");
            }
        }

        private void requireState(State expected, String operation) {
            if (state != expected) {
                throw new IllegalStateException(
                        "Cannot " + operation + " when KV cache session is " + state
                                + " (expected " + expected + ")");
            }
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
            if (failure == null) {
                return;
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("KV cache session cleanup failed", failure);
        }

        // Legacy compatibility - will be replaced by PagedAttention.
        public AccelTensor keyCache(int layerIdx) {
            throw new UnsupportedOperationException("KVCache is now paged. Use getBlockTable().");
        }

        public AccelTensor valueCache(int layerIdx) {
            throw new UnsupportedOperationException("KVCache is now paged. Use getBlockTable().");
        }
    }
}

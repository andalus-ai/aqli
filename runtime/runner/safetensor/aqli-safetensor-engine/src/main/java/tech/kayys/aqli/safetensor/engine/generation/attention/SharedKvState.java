/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.attention;

import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;
import tech.kayys.aqli.safetensor.engine.generation.kv.KvCacheMemoryBudget;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Retains dense shared-layer K/V state and optional packed Metal views.
 *
 * <p>Both the token-major backing tensors and their optional packed copies are
 * charged to the same process-wide KV memory budget as paged block storage.
 */
public class SharedKvState implements AutoCloseable {
    private AccelTensor keyBuffer;
    private AccelTensor valueBuffer;
    private AccelTensor packedKeyBuffer;
    private AccelTensor packedValueBuffer;
    private final int numHeads;
    private final int headDim;
    private final KvCacheMemoryBudget memoryBudget;
    private KvCacheMemoryBudget.Lease baseMemoryLease;
    private KvCacheMemoryBudget.Lease packedMemoryLease;
    private int lengthTokens;
    private int capacityTokens;

    public SharedKvState(AccelTensor key, AccelTensor value) {
        this(key, value, new KvCacheMemoryBudget(Long.MAX_VALUE));
    }

    public SharedKvState(AccelTensor key, AccelTensor value, KvCacheMemoryBudget memoryBudget) {
        this.keyBuffer = Objects.requireNonNull(key, "key");
        this.valueBuffer = Objects.requireNonNull(value, "value");
        this.memoryBudget = Objects.requireNonNull(memoryBudget, "memoryBudget");
        this.numHeads = Math.toIntExact(key.size(2));
        this.headDim = Math.toIntExact(key.size(3));
        this.lengthTokens = Math.toIntExact(key.size(1));
        this.capacityTokens = lengthTokens;
        try {
            this.baseMemoryLease = reserveIfPositive(
                    Math.addExact(key.dataSegment().byteSize(), value.dataSegment().byteSize()));
        } catch (RuntimeException | Error failure) {
            closeIfOpen(keyBuffer);
            closeIfOpen(valueBuffer);
            throw failure;
        }
    }

    public AccelTensor key() {
        return SharedKvBufferOps.viewTokenMajor(keyBuffer, lengthTokens);
    }

    public AccelTensor value() {
        return SharedKvBufferOps.viewTokenMajor(valueBuffer, lengthTokens);
    }

    public void releaseView(AccelTensor tensor) {
        if (tensor != null
                && tensor != keyBuffer
                && tensor != valueBuffer
                && !tensor.isClosed()) {
            tensor.close();
        }
    }

    public void append(AccelTensor deltaKey, AccelTensor deltaValue) {
        int deltaTokens = Math.toIntExact(deltaKey.size(1));
        if (deltaTokens <= 0) {
            closeIfView(deltaKey, keyBuffer);
            closeIfView(deltaValue, valueBuffer);
            return;
        }

        try {
            ensureCapacity(Math.addExact(lengthTokens, deltaTokens));
            SharedKvBufferOps.appendTokenMajor(keyBuffer, deltaKey, lengthTokens, numHeads, headDim);
            SharedKvBufferOps.appendTokenMajor(valueBuffer, deltaValue, lengthTokens, numHeads, headDim);
            if (packedKeyBuffer != null && packedValueBuffer != null) {
                SharedKvBufferOps.appendTokenMajorToHeadMajor(
                        packedKeyBuffer, deltaKey, lengthTokens, numHeads, headDim);
                SharedKvBufferOps.appendTokenMajorToHeadMajor(
                        packedValueBuffer, deltaValue, lengthTokens, numHeads, headDim);
            }
            lengthTokens += deltaTokens;
        } finally {
            closeIfView(deltaKey, keyBuffer);
            closeIfView(deltaValue, valueBuffer);
        }
    }

    public MemorySegment packedKeyData() {
        ensurePackedBuffers();
        return packedKeyBuffer.dataPtr();
    }

    public MemorySegment packedValueData() {
        ensurePackedBuffers();
        return packedValueBuffer.dataPtr();
    }

    public int packedCapacityTokens() {
        ensurePackedBuffers();
        return capacityTokens;
    }

    private void ensureCapacity(int requiredTokens) {
        if (requiredTokens <= capacityTokens) {
            return;
        }
        int newCapacity = Math.max(requiredTokens, Math.max(4, Math.multiplyExact(capacityTokens, 2)));
        long batch = keyBuffer.size(0);
        long newBufferBytes = tensorBytes(batch, newCapacity, numHeads, headDim);
        long newBaseBytes = Math.multiplyExact(newBufferBytes, 2L);

        KvCacheMemoryBudget.Lease newBaseLease = reserveIfPositive(newBaseBytes);
        KvCacheMemoryBudget.Lease newPackedLease = null;
        boolean growPacked = packedKeyBuffer != null && packedValueBuffer != null;
        AccelTensor newKey = null;
        AccelTensor newValue = null;
        AccelTensor newPackedKey = null;
        AccelTensor newPackedValue = null;
        try {
            if (growPacked) {
                newPackedLease = reserveIfPositive(newBaseBytes);
            }

            newKey = AccelTensor.zeros(batch, newCapacity, numHeads, headDim);
            newValue = AccelTensor.zeros(batch, newCapacity, numHeads, headDim);
            SharedKvBufferOps.copyTokenMajorRange(keyBuffer, newKey, lengthTokens, numHeads, headDim);
            SharedKvBufferOps.copyTokenMajorRange(valueBuffer, newValue, lengthTokens, numHeads, headDim);

            if (newPackedLease != null) {
                newPackedKey = AccelTensor.zeros(batch, numHeads, newCapacity, headDim);
                newPackedValue = AccelTensor.zeros(batch, numHeads, newCapacity, headDim);
                SharedKvBufferOps.copyHeadMajorRange(
                        packedKeyBuffer, newPackedKey, lengthTokens, numHeads, headDim);
                SharedKvBufferOps.copyHeadMajorRange(
                        packedValueBuffer, newPackedValue, lengthTokens, numHeads, headDim);
            }
        } catch (RuntimeException | Error failure) {
            closeIfOpen(newKey);
            closeIfOpen(newValue);
            closeIfOpen(newPackedKey);
            closeIfOpen(newPackedValue);
            closeLease(newBaseLease);
            if (newPackedLease != null) {
                newPackedLease.close();
            }
            throw failure;
        }

        closeIfOpen(keyBuffer);
        closeIfOpen(valueBuffer);
        closeLease(baseMemoryLease);
        keyBuffer = newKey;
        valueBuffer = newValue;
        baseMemoryLease = newBaseLease;

        if (growPacked) {
            closeIfOpen(packedKeyBuffer);
            closeIfOpen(packedValueBuffer);
            closeLease(packedMemoryLease);
            packedKeyBuffer = newPackedKey;
            packedValueBuffer = newPackedValue;
            packedMemoryLease = newPackedLease;
        }
        capacityTokens = newCapacity;
    }

    private void ensurePackedBuffers() {
        if (packedKeyBuffer != null && packedValueBuffer != null) {
            return;
        }
        long batch = keyBuffer.size(0);
        long bytes = Math.multiplyExact(tensorBytes(batch, capacityTokens, numHeads, headDim), 2L);
        KvCacheMemoryBudget.Lease lease = reserveIfPositive(bytes);
        AccelTensor newPackedKey = null;
        AccelTensor newPackedValue = null;
        try {
            newPackedKey = AccelTensor.zeros(batch, numHeads, capacityTokens, headDim);
            newPackedValue = AccelTensor.zeros(batch, numHeads, capacityTokens, headDim);
            SharedKvBufferOps.packTokenMajorToHeadMajor(
                    keyBuffer, newPackedKey, lengthTokens, numHeads, headDim);
            SharedKvBufferOps.packTokenMajorToHeadMajor(
                    valueBuffer, newPackedValue, lengthTokens, numHeads, headDim);
        } catch (RuntimeException | Error failure) {
            closeIfOpen(newPackedKey);
            closeIfOpen(newPackedValue);
            closeLease(lease);
            throw failure;
        }
        packedKeyBuffer = newPackedKey;
        packedValueBuffer = newPackedValue;
        packedMemoryLease = lease;
    }

    private KvCacheMemoryBudget.Lease reserveIfPositive(long bytes) {
        return bytes > 0 ? memoryBudget.reserve(bytes) : null;
    }

    private static void closeLease(KvCacheMemoryBudget.Lease lease) {
        if (lease != null) {
            lease.close();
        }
    }

    private static long tensorBytes(long batch, int tokens, int heads, int headDim) {
        long elements = Math.multiplyExact(batch,
                Math.multiplyExact((long) tokens, Math.multiplyExact((long) heads, headDim)));
        return Math.multiplyExact(elements, Float.BYTES);
    }

    private static void closeIfView(AccelTensor tensor, AccelTensor backingBuffer) {
        if (tensor != null && tensor != backingBuffer && !tensor.isClosed()) {
            tensor.close();
        }
    }

    private static void closeIfOpen(AccelTensor tensor) {
        if (tensor != null && !tensor.isClosed()) {
            tensor.close();
        }
    }

    @Override
    public void close() {
        closeIfOpen(keyBuffer);
        closeIfOpen(valueBuffer);
        closeIfOpen(packedKeyBuffer);
        closeIfOpen(packedValueBuffer);
        closeLease(baseMemoryLease);
        baseMemoryLease = null;
        closeLease(packedMemoryLease);
        packedMemoryLease = null;
    }
}

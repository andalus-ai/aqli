/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.kv;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide admission budget for native Safetensors KV-cache slabs.
 *
 * <p>The budget accounts reservations, not JVM heap. A lease remains held
 * while its KV session is alive, including when a conversation retains it.
 */
@ApplicationScoped
public class KvCacheMemoryBudget {
    static final long DEFAULT_MAX_NATIVE_BYTES = 8L * 1024 * 1024 * 1024;

    private final long maxBytes;
    private long reservedBytes;

    @Inject
    public KvCacheMemoryBudget(
            @ConfigProperty(name = "aqli.safetensor.kv-cache.max-native-bytes",
                    defaultValue = "8589934592") long maxBytes) {
        this(maxBytes, true);
    }

    KvCacheMemoryBudget(long maxBytes) {
        this(maxBytes, true);
    }

    private KvCacheMemoryBudget(long maxBytes, boolean validate) {
        if (validate && maxBytes <= 0) {
            throw new IllegalArgumentException("KV cache native memory budget must be positive");
        }
        this.maxBytes = maxBytes;
    }

    static KvCacheMemoryBudget unbounded() {
        return new KvCacheMemoryBudget(Long.MAX_VALUE, false);
    }

    public synchronized Lease reserve(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("KV cache reservation must be positive");
        }
        long next;
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
                "KV cache native memory budget exceeded: requested " + requestedBytes
                        + " bytes, reserved " + reservedBytes + " of " + maxBytes + " bytes");
    }

    private synchronized void release(long bytes) {
        if (bytes > reservedBytes) {
            throw new IllegalStateException("KV cache memory budget release exceeds outstanding reservation");
        }
        reservedBytes -= bytes;
    }

    public static final class Lease implements AutoCloseable {
        private final KvCacheMemoryBudget owner;
        private final long bytes;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(KvCacheMemoryBudget owner, long bytes) {
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

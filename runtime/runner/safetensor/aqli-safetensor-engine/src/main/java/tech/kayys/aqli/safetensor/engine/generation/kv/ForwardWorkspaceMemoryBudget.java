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
 * Process-wide admission budget for persistent direct-forward workspace arenas.
 *
 * <p>Reservations cover the aligned native allocations retained by each
 * {@link ForwardWorkspace}. During growth, old and replacement arenas are
 * both counted until the replacement is fully allocated and installed.
 */
@ApplicationScoped
public class ForwardWorkspaceMemoryBudget {
    static final long DEFAULT_MAX_NATIVE_BYTES = 2L * 1024 * 1024 * 1024;

    private final long maxBytes;
    private long reservedBytes;

    @Inject
    public ForwardWorkspaceMemoryBudget(
            @ConfigProperty(name = "aqli.safetensor.forward-workspace.max-native-bytes",
                    defaultValue = "2147483648") long maxBytes) {
        this(maxBytes, true);
    }

    private ForwardWorkspaceMemoryBudget(long maxBytes, boolean validate) {
        if (validate && maxBytes <= 0) {
            throw new IllegalArgumentException("Forward workspace native memory budget must be positive");
        }
        this.maxBytes = maxBytes;
    }

    static ForwardWorkspaceMemoryBudget unbounded() {
        return new ForwardWorkspaceMemoryBudget(Long.MAX_VALUE, false);
    }

    public synchronized Lease reserve(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("Forward workspace reservation must be positive");
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
                "Forward workspace native memory budget exceeded: requested " + requestedBytes
                        + " bytes, reserved " + reservedBytes + " of " + maxBytes + " bytes");
    }

    private synchronized void release(long bytes) {
        if (bytes > reservedBytes) {
            throw new IllegalStateException("Forward workspace memory release exceeds outstanding reservation");
        }
        reservedBytes -= bytes;
    }

    public static final class Lease implements AutoCloseable {
        private final ForwardWorkspaceMemoryBudget owner;
        private final long bytes;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(ForwardWorkspaceMemoryBudget owner, long bytes) {
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

/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.kv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttentionScratchMemoryBudgetTest {

    @Test
    void reservesAndReleasesConcurrentDispatchCapacity() {
        AttentionScratchMemoryBudget budget = new AttentionScratchMemoryBudget(1024);

        var first = budget.reserve(384);
        var second = budget.reserve(512);
        assertEquals(896, budget.reservedBytes());

        assertThrows(AttentionScratchMemoryBudget.ExceededException.class,
                () -> budget.reserve(256));

        first.close();
        assertEquals(512, budget.reservedBytes());
        second.close();
        second.close();
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void rejectsInvalidLimitsAndReservations() {
        assertThrows(IllegalArgumentException.class, () -> new AttentionScratchMemoryBudget(0));
        AttentionScratchMemoryBudget budget = new AttentionScratchMemoryBudget(1024);
        assertThrows(IllegalArgumentException.class, () -> budget.reserve(0));
        assertThrows(IllegalArgumentException.class, () -> budget.reserve(-1));
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void sessionUsesTheInjectedProcessWideAttentionBudget() {
        KvCacheMemoryBudget kvBudget = new KvCacheMemoryBudget(1024 * 1024);
        ForwardWorkspaceMemoryBudget workspaceBudget = new ForwardWorkspaceMemoryBudget(1024 * 1024);
        AttentionScratchMemoryBudget scratchBudget = new AttentionScratchMemoryBudget(1024);
        KVCacheManager.KVCacheSession session = new KVCacheManager.KVCacheSession(
                128, new BlockManager(kvBudget), kvBudget, workspaceBudget, scratchBudget);

        assertSame(scratchBudget, session.attentionScratchMemoryBudget());
        session.close();
    }
}

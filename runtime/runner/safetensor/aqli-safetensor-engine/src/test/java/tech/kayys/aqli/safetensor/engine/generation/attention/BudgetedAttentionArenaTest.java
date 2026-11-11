/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.attention;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.safetensor.engine.generation.kv.AttentionScratchMemoryBudget;

import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

class BudgetedAttentionArenaTest {

    @Test
    void accountsEveryAllocationUntilDispatchArenaCloses() {
        AttentionScratchMemoryBudget budget = new AttentionScratchMemoryBudget(1024);
        MemorySegment first;
        MemorySegment second;
        try (BudgetedAttentionArena arena = new BudgetedAttentionArena(budget)) {
            first = arena.allocate(256, 64);
            second = arena.allocate(128, Integer.BYTES);
            assertEquals(384, arena.reservedBytes());
            assertEquals(384, budget.reservedBytes());
            assertTrue(first.scope().isAlive());
            assertTrue(second.scope().isAlive());
        }

        assertEquals(0, budget.reservedBytes());
        assertFalse(first.scope().isAlive());
        assertFalse(second.scope().isAlive());
    }

    @Test
    void rejectsAllocationBeforeNativeMemoryIsCreated() {
        AttentionScratchMemoryBudget budget = new AttentionScratchMemoryBudget(256);
        try (BudgetedAttentionArena arena = new BudgetedAttentionArena(budget)) {
            MemorySegment first = arena.allocate(192, 64);
            assertThrows(AttentionScratchMemoryBudget.ExceededException.class,
                    () -> arena.allocate(128, 64));
            assertTrue(first.scope().isAlive());
            assertEquals(192, budget.reservedBytes());
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void releasesReservationWhenArenaAllocationFails() {
        AttentionScratchMemoryBudget budget = new AttentionScratchMemoryBudget(256);
        try (BudgetedAttentionArena arena = new BudgetedAttentionArena(budget)) {
            assertThrows(IllegalArgumentException.class, () -> arena.allocate(64, 3));
            assertEquals(0, budget.reservedBytes());
        }
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void rejectsUseAfterClose() {
        AttentionScratchMemoryBudget budget = new AttentionScratchMemoryBudget(256);
        BudgetedAttentionArena arena = new BudgetedAttentionArena(budget);
        arena.close();
        arena.close();
        assertThrows(IllegalStateException.class, () -> arena.allocate(16, 8));
        assertEquals(0, budget.reservedBytes());
    }
}

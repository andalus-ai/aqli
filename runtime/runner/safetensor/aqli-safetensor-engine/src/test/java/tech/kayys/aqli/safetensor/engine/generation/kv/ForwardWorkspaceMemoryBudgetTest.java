/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.generation.kv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForwardWorkspaceMemoryBudgetTest {

    @Test
    void accountsEveryWorkspaceArenaAndReleasesReservationsOnClose() {
        ForwardWorkspaceMemoryBudget budget = new ForwardWorkspaceMemoryBudget(64 * 1024);
        ForwardWorkspace workspace = new ForwardWorkspace(budget);

        workspace.ensureCapacity(4, 4, 8);
        assertEquals(20_480, budget.reservedBytes());

        workspace.ensureProjectionScratchCapacity(32);
        workspace.ensureLogitsCapacity(3);
        workspace.ensureAttentionMetadataCapacity(4, 1);
        assertEquals(32_788, budget.reservedBytes());
        assertEquals(budget.reservedBytes(), workspace.allocatedNativeBytes());

        workspace.close();
        workspace.close();
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void rejectsGrowthWithoutInvalidatingPreviouslyAllocatedScratch() {
        ForwardWorkspaceMemoryBudget budget = new ForwardWorkspaceMemoryBudget(32_768);
        ForwardWorkspace workspace = new ForwardWorkspace(budget);
        workspace.ensureCapacity(4, 4, 8);

        var hidden = workspace.getHiddenASeg();
        var combined = workspace.getCombinedSeg();
        assertEquals(20_480, budget.reservedBytes());

        assertThrows(IllegalStateException.class,
                () -> workspace.ensureCapacity(4_096, 4, 8));

        assertSame(hidden, workspace.getHiddenASeg());
        assertSame(combined, workspace.getCombinedSeg());
        assertTrue(hidden.scope().isAlive());
        assertEquals(20_480, budget.reservedBytes());

        workspace.close();
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void successfulGrowthReleasesOldArenaReservations() {
        ForwardWorkspaceMemoryBudget budget = new ForwardWorkspaceMemoryBudget(256 * 1024);
        ForwardWorkspace workspace = new ForwardWorkspace(budget);
        workspace.ensureCapacity(4, 4, 8);
        assertEquals(20_480, budget.reservedBytes());

        workspace.ensureCapacity(4_096, 4, 8);

        assertEquals(98_304, budget.reservedBytes());
        assertEquals(budget.reservedBytes(), workspace.allocatedNativeBytes());
        workspace.close();
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void rejectsDimensionOverflowBeforeAllocating() {
        ForwardWorkspaceMemoryBudget budget = new ForwardWorkspaceMemoryBudget(1024);
        ForwardWorkspace workspace = new ForwardWorkspace(budget);
        assertThrows(ArithmeticException.class,
                () -> workspace.ensureCapacity(Long.MAX_VALUE, 1, 1));
        assertEquals(0, budget.reservedBytes());
        workspace.close();
    }

    @Test
    void closedWorkspaceCannotBeReused() {
        ForwardWorkspace workspace = new ForwardWorkspace();
        workspace.close();
        assertThrows(IllegalStateException.class, () -> workspace.ensureCapacity(1, 1, 1));
        assertThrows(IllegalStateException.class, workspace::getHiddenASeg);
    }
}

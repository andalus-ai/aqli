package tech.kayys.aqli.safetensor.engine.generation.kv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockManagerTest {

    @Test
    void leasesEachPhysicalBlockOnlyOnceUntilReleased() {
        try (BlockManager manager = new BlockManager()) {
            manager.initialize(16, 1, 8, 2);

            Integer first = manager.allocateBlock();
            Integer second = manager.allocateBlock();

            assertEquals(0, first);
            assertEquals(1, second);
            assertNull(manager.allocateBlock());
            assertEquals(2, manager.getAllocatedBlockCount());

            manager.freeBlock(first);
            assertEquals(first, manager.allocateBlock());
            assertEquals(2, manager.getAllocatedBlockCount());
        }
    }

    @Test
    void rejectsDuplicateAndInvalidReleases() {
        try (BlockManager manager = new BlockManager()) {
            manager.initialize(16, 1, 8, 2);
            int block = manager.allocateBlock();

            manager.freeBlock(block);
            assertThrows(IllegalStateException.class, () -> manager.freeBlock(block));
            assertThrows(IndexOutOfBoundsException.class, () -> manager.freeBlock(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> manager.freeBlock(2));
        }
    }

    @Test
    void sameConfigurationDoesNotResetOutstandingLeases() {
        try (BlockManager manager = new BlockManager()) {
            manager.initialize(16, 1, 8, 2);
            int block = manager.allocateBlock();

            manager.initialize(16, 1, 8, 2);

            assertEquals(1, manager.getAllocatedBlockCount());
            assertEquals(1, manager.allocateBlock());
            assertNull(manager.allocateBlock());
            assertEquals(512, manager.getKBlock(block).byteSize());
        }
    }

    @Test
    void rejectsReconfigurationWhileBlocksAreLeased() {
        try (BlockManager manager = new BlockManager()) {
            manager.initialize(16, 1, 8, 2);
            manager.allocateBlock();

            assertThrows(IllegalStateException.class,
                    () -> manager.initialize(16, 1, 8, 3));
            assertEquals(2, manager.getCapacity());
        }
    }

    @Test
    void validatesDimensionsAndBlockBounds() {
        try (BlockManager manager = new BlockManager()) {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.initialize(0, 1, 8, 2));
            manager.initialize(16, 1, 8, 2);

            assertThrows(IndexOutOfBoundsException.class, () -> manager.getKBlock(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> manager.getVBlock(2));
            assertThrows(IndexOutOfBoundsException.class, () -> manager.getKScaleBlock(2));
        }
    }

    @Test
    void closeIsIdempotentAndRequiresReinitializationBeforeUse() {
        BlockManager manager = new BlockManager();
        manager.initialize(16, 1, 8, 1);
        manager.close();
        manager.close();

        assertThrows(IllegalStateException.class, manager::allocateBlock);
        manager.initialize(16, 1, 8, 1);
        assertEquals(0, manager.allocateBlock());
        manager.close();
    }
}

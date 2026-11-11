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
    void allocatesPhysicalStorageOnlyForChunksThatContainLeasedBlocks() {
        try (BlockManager manager = new BlockManager()) {
            manager.initialize(16, 1, 8, 40);

            assertEquals(0, manager.getAllocatedChunkCount());
            assertEquals(0, manager.getAllocatedNativeBytes());

            for (int i = 0; i < 16; i++) {
                assertEquals(i, manager.allocateBlock());
            }
            assertEquals(1, manager.getAllocatedChunkCount());
            assertEquals(16L * 16 * 1 * 8 * Float.BYTES * 2, manager.getAllocatedNativeBytes());

            assertEquals(16, manager.allocateBlock());
            assertEquals(2, manager.getAllocatedChunkCount());

            for (int i = 0; i < 16; i++) {
                manager.freeBlock(i);
            }
            assertEquals(1, manager.getAllocatedChunkCount());
            assertThrows(IllegalStateException.class, () -> manager.getKBlock(0));
        }
    }

    @Test
    void allocatesQuantizationScaleChunksWithTheirDataChunks() {
        try (BlockManager manager = new BlockManager()) {
            manager.initialize(16, 2, 8, 17, BlockManager.KvStorageType.INT8);

            assertEquals(0, manager.getAllocatedNativeBytes());
            manager.allocateBlock();

            long dataBytes = 16L * 16 * 2 * 8 * 2;
            long scaleBytes = 16L * 2 * 16 * Float.BYTES * 2;
            assertEquals(dataBytes + scaleBytes, manager.getAllocatedNativeBytes());
            assertEquals(1, manager.getAllocatedChunkCount());
            assertEquals(16L * 2 * 16 * Float.BYTES, manager.getKScaleBlock(0).byteSize());
        }
    }

    @Test
    void rawPoolAccessIsRejectedWhenCapacitySpansMultipleChunks() {
        try (BlockManager manager = new BlockManager()) {
            manager.initialize(16, 1, 8, 40);
            manager.allocateBlock();

            assertThrows(UnsupportedOperationException.class, manager::getRawKPool);
            assertThrows(UnsupportedOperationException.class, manager::getRawVPool);
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

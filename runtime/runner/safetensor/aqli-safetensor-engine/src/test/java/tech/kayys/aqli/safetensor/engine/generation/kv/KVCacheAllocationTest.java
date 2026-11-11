package tech.kayys.aqli.safetensor.engine.generation.kv;

import tech.kayys.aljabr.spi.model.ModelConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KVCacheAllocationTest {

    @Test
    void sessionsOwnIndependentBlockManagers() {
        KVCacheManager manager = new KVCacheManager();

        KVCacheManager.KVCacheSession first = manager.createSession(128);
        KVCacheManager.KVCacheSession second = manager.createSession(128);

        assertNotSame(first.blockManager(), second.blockManager());

        first.close();
        assertDoesNotThrow(() -> second.blockManager().initialize(
                16, 1, 64, 8, BlockManager.KvStorageType.FP32));
        second.close();
    }

    @Test
    void allocationIsSingleUseAndSecondAttemptDoesNotLoseLeases() {
        try (KVCacheManager.KVCacheSession session = new KVCacheManager().createSession(128)) {
            ModelConfig config = modelConfig();
            var generationConfig = tech.kayys.aljabr.safetensor.generation.GenerationConfig.defaults();

            session.allocate(config, generationConfig);
            BlockManager blockManager = session.blockManager();
            int leasedBeforeRetry = blockManager.getAllocatedBlockCount();
            List<Integer> originalTable = List.copyOf(session.getBlockTable(0));

            assertThrows(IllegalStateException.class, () -> session.allocate(config, generationConfig));

            assertEquals(leasedBeforeRetry, blockManager.getAllocatedBlockCount());
            assertEquals(originalTable, session.getBlockTable(0));
        }
    }

    @Test
    void closeIsIdempotentAndClosedSessionRejectsFurtherUse() {
        KVCacheManager.KVCacheSession session = new KVCacheManager().createSession(128);
        session.allocate(modelConfig(),
                tech.kayys.aljabr.safetensor.generation.GenerationConfig.defaults());
        BlockManager blockManager = session.blockManager();

        session.close();
        session.close();

        assertEquals(0, blockManager.getAllocatedBlockCount());
        assertThrows(IllegalStateException.class, () -> session.allocate(
                modelConfig(), tech.kayys.aljabr.safetensor.generation.GenerationConfig.defaults()));
        assertThrows(IllegalStateException.class, () -> session.ensureCapacity(16));
        assertThrows(IllegalStateException.class, session::getRawKPool);
        assertThrows(IllegalStateException.class, session::getWorkspace);
        assertThrows(IllegalStateException.class, session::blockManager);
    }

    @Test
    void testProactiveAllocation() {
        try (KVCacheManager.KVCacheSession session = new KVCacheManager().createSession(128)) {
            session.allocate(modelConfig(),
                    tech.kayys.aljabr.safetensor.generation.GenerationConfig.defaults());

            // Initially 1 block allocated per layer (tokens 0-15).
            assertEquals(1, session.getBlockTable(0).size());

            session.ensureCapacity(35);
            assertEquals(3, session.getBlockTable(0).size());

            session.ensureCapacity(16);
            assertEquals(3, session.getBlockTable(0).size());

            session.ensureCapacity(49);
            assertEquals(4, session.getBlockTable(0).size());
        }
    }

    @Test
    void ensureCapacityRejectsRequestsBeyondConfiguredSequenceLimitWithoutMutation() {
        try (KVCacheManager.KVCacheSession session = new KVCacheManager().createSession(32)) {
            session.allocate(modelConfig(), tech.kayys.aljabr.safetensor.generation.GenerationConfig.defaults());
            int allocatedBefore = session.blockManager().getAllocatedBlockCount();
            List<Integer> tableBefore = List.copyOf(session.getBlockTable(0));

            assertThrows(IllegalArgumentException.class, () -> session.ensureCapacity(33));

            assertEquals(allocatedBefore, session.blockManager().getAllocatedBlockCount());
            assertEquals(tableBefore, session.getBlockTable(0));
        }
    }

    @Test
    void advanceRejectsSequenceOverflowWithoutChangingPosition() {
        try (KVCacheManager.KVCacheSession session = new KVCacheManager().createSession(32)) {
            session.allocate(modelConfig(), tech.kayys.aljabr.safetensor.generation.GenerationConfig.defaults());
            session.advance(24);
            int allocatedBefore = session.blockManager().getAllocatedBlockCount();
            List<Integer> tableBefore = List.copyOf(session.getBlockTable(0));

            assertThrows(IllegalArgumentException.class, () -> session.advance(9));

            assertEquals(24, session.currentPos());
            assertEquals(allocatedBefore, session.blockManager().getAllocatedBlockCount());
            assertEquals(tableBefore, session.getBlockTable(0));
        }
    }

    @Test
    void getBlockForTokenRejectsTokenOutsideConfiguredSequenceLimit() {
        try (KVCacheManager.KVCacheSession session = new KVCacheManager().createSession(32)) {
            session.allocate(modelConfig(), tech.kayys.aljabr.safetensor.generation.GenerationConfig.defaults());

            assertThrows(IllegalArgumentException.class, () -> session.getBlockForToken(0, 32));
            assertEquals(1, session.getBlockTable(0).size());
        }
    }

    @Test
    void testInt4KvAllocationUsesPackedBlocks() {
        try (KVCacheManager.KVCacheSession session = new KVCacheManager().createSession(128)) {
            var cfg = tech.kayys.aljabr.safetensor.generation.GenerationConfig.builder()
                    .kvCacheQuant(tech.kayys.aljabr.safetensor.generation.GenerationConfig.KvCacheQuantization.INT4)
                    .build();

            session.allocate(modelConfig(), cfg);

            assertTrue(session.isQuantizedInt4());
            assertFalse(session.isQuantizedInt8());
            assertEquals(BlockManager.KvStorageType.INT4, session.blockManager().getStorageType());
            assertEquals(512, session.blockManager().getBlockSizeBytes(),
                    "16 tokens * 64 dims packed to 4-bit should consume 512 bytes per block");
            assertNotNull(session.blockManager().getKScaleBlock(session.getBlockTable(0).getFirst()));
        }
    }

    private static ModelConfig modelConfig() {
        ModelConfig config = new ModelConfig();
        config.setArchitectures(List.of("LlamaForCausalLM"));
        config.overrideNumAttentionHeads(1);
        config.overrideNumKeyValueHeads(1);
        config.overrideHeadDim(64);
        return config;
    }
}

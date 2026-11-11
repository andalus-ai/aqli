package tech.kayys.aqli.safetensor.engine.generation.kv;

import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.spi.model.ModelConfig;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KvCacheMemoryBudgetTest {
    @Test
    void estimatorMatchesFp32Int8AndInt4SlabLayouts() {
        var fp32 = KvCacheMemoryEstimator.estimate(32, 2, 2, 8, 16, BlockManager.KvStorageType.FP32, 0);
        assertEquals(4, fp32.blockCount());
        assertEquals(8_192, fp32.dataSlabBytes());
        assertEquals(0, fp32.scaleSlabBytes());
        assertEquals(8_192, fp32.totalBytes());

        var int8 = KvCacheMemoryEstimator.estimate(32, 2, 2, 8, 16, BlockManager.KvStorageType.INT8, 0);
        assertEquals(2_048, int8.dataSlabBytes());
        assertEquals(1_024, int8.scaleSlabBytes());
        assertEquals(3_072, int8.totalBytes());

        var int4 = KvCacheMemoryEstimator.estimate(32, 2, 2, 8, 16, BlockManager.KvStorageType.INT4, 0);
        assertEquals(1_024, int4.dataSlabBytes());
        assertEquals(1_024, int4.scaleSlabBytes());
        assertEquals(2_048, int4.totalBytes());
    }

    @Test
    void estimatorIncludesPerLayerCapacityAndAllocatorMargin() {
        var estimate = KvCacheMemoryEstimator.estimate(
                17, 3, 2, 8, 16, BlockManager.KvStorageType.FP32, 64);
        assertEquals(70, estimate.blockCount());
        assertEquals(70L * 2 * 16 * 2 * 8 * Float.BYTES, estimate.totalBytes());
    }

    @Test
    void budgetRejectsReservationsThatWouldExceedLimit() {
        KvCacheMemoryBudget budget = new KvCacheMemoryBudget(100);
        var lease = budget.reserve(60);
        assertEquals(60, budget.reservedBytes());
        assertThrows(IllegalStateException.class, () -> budget.reserve(41));
        assertEquals(60, budget.reservedBytes());
        lease.close();
        lease.close();
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void concurrentReservationsCannotOvercommitBudget() throws Exception {
        KvCacheMemoryBudget budget = new KvCacheMemoryBudget(10);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger acquired = new AtomicInteger();
        try {
            var futures = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        try (var ignored = budget.reserve(1)) {
                            acquired.incrementAndGet();
                            Thread.sleep(5);
                        } catch (IllegalStateException expected) {
                            // The fixed budget rejects excess concurrent sessions.
                        }
                        return null;
                    })).toList();
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        assertTrue(acquired.get() <= 10);
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void sessionHoldsBudgetUntilCloseAndReleasesAfterAllocationFailure() {
        ModelConfig config = modelConfig();
        var generationConfig = tech.kayys.aljabr.safetensor.generation.GenerationConfig.defaults();
        var estimate = KvCacheMemoryEstimator.estimate(
                128, config.numHiddenLayers(), config.resolvedMaxKvHeads(), config.resolvedMaxHeadDim(),
                16, BlockManager.KvStorageType.FP32, 64);

        KvCacheMemoryBudget budget = new KvCacheMemoryBudget(estimate.totalBytes());
        KVCacheManager.KVCacheSession session =
                new KVCacheManager.KVCacheSession(128, new BlockManager(budget));
        session.allocate(config, generationConfig);
        assertEquals(session.blockManager().getAllocatedNativeBytes(), budget.reservedBytes());
        assertTrue(budget.reservedBytes() < estimate.totalBytes(),
                "lazy allocation should reserve only chunks currently materialized");
        session.close();
        assertEquals(0, budget.reservedBytes());

        KvCacheMemoryBudget insufficient = new KvCacheMemoryBudget(1);
        KVCacheManager.KVCacheSession rejected =
                new KVCacheManager.KVCacheSession(128, new BlockManager(insufficient));
        assertThrows(IllegalStateException.class, () -> rejected.allocate(config, generationConfig));
        assertEquals(0, insufficient.reservedBytes());
        rejected.close();
    }

    @Test
    void estimatorRejectsOverflowAndInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> KvCacheMemoryEstimator.estimate(
                0, 1, 1, 1, 16, BlockManager.KvStorageType.FP32, 0));
        assertThrows(ArithmeticException.class, () -> KvCacheMemoryEstimator.estimate(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                16, BlockManager.KvStorageType.FP32, Integer.MAX_VALUE));
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

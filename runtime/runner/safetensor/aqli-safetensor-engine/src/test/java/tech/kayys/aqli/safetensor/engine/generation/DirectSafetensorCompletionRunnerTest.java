package tech.kayys.aqli.safetensor.engine.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import tech.kayys.aljabr.safetensor.generation.GenerationConfig;

class DirectSafetensorCompletionRunnerTest {

    @Test
    void samplingStrategyPreservesDirectCliSelectionRules() {
        assertEquals(
                GenerationConfig.SamplingStrategy.GREEDY,
                DirectSafetensorCompletionRunner.resolveSamplingStrategy(0.0d, 0, 0.0d));
        assertEquals(
                GenerationConfig.SamplingStrategy.GREEDY,
                DirectSafetensorCompletionRunner.resolveSamplingStrategy(0.8d, 1, 0.9d));
        assertEquals(
                GenerationConfig.SamplingStrategy.TOP_K_TOP_P,
                DirectSafetensorCompletionRunner.resolveSamplingStrategy(0.8d, 40, 0.9d));
        assertEquals(
                GenerationConfig.SamplingStrategy.TOP_P,
                DirectSafetensorCompletionRunner.resolveSamplingStrategy(0.8d, 0, 0.9d));
        assertEquals(
                GenerationConfig.SamplingStrategy.TOP_K,
                DirectSafetensorCompletionRunner.resolveSamplingStrategy(0.8d, 40, 1.0d));
    }

    @Test
    void kvCacheQuantizationPreservesCliAliases() {
        assertEquals(
                GenerationConfig.KvCacheQuantization.NONE,
                DirectSafetensorCompletionRunner.resolveKvCacheQuantization(null));
        assertEquals(
                GenerationConfig.KvCacheQuantization.NONE,
                DirectSafetensorCompletionRunner.resolveKvCacheQuantization("none"));
        assertEquals(
                GenerationConfig.KvCacheQuantization.INT8,
                DirectSafetensorCompletionRunner.resolveKvCacheQuantization("int8"));
        assertEquals(
                GenerationConfig.KvCacheQuantization.INT4,
                DirectSafetensorCompletionRunner.resolveKvCacheQuantization("int4"));
        assertEquals(
                GenerationConfig.KvCacheQuantization.INT4,
                DirectSafetensorCompletionRunner.resolveKvCacheQuantization("turbo"));
    }
}

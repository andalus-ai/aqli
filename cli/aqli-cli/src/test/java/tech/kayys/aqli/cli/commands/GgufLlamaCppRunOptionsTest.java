package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GgufLlamaCppRunOptionsTest {

    @Test
    void resolvesDefaultMetalSessionOptions() {
        GgufLlamaCppRunOptions options = GgufLlamaCppRunOptions.resolve(
                "metal", "hello", 32, false, 8, configuration(Map.of()));

        assertEquals(512, options.context());
        assertEquals(512, options.batch());
        assertEquals(512, options.microBatch());
        assertEquals(8, options.threads());
        assertEquals(8, options.batchThreads());
        assertEquals(-1, options.gpuLayers());
        assertFalse(options.swaFull());
        assertTrue(options.useMmap());
        assertFalse(options.useMlock());
    }

    @Test
    void resolvesCpuAndConfiguredSessionOptions() {
        GgufLlamaCppRunOptions options = GgufLlamaCppRunOptions.resolve(
                "cpu",
                "a longer prompt",
                64,
                false,
                8,
                configuration(Map.of(
                        "aqli.gguf.fast_run.context", "2048",
                        "aqli.gguf.fast_run.batch", "256",
                        "aqli.gguf.fast_run.ubatch", "64",
                        "aqli.gguf.fast_run.threads", "6",
                        "aqli.gguf.fast_run.batch_threads", "4",
                        "aqli.gguf.fast_run.swa_full", "true",
                        "aqli.gguf.fast_run.mmap", "false",
                        "aqli.gguf.fast_run.mlock", "true")));

        assertEquals(2048, options.context());
        assertEquals(256, options.batch());
        assertEquals(64, options.microBatch());
        assertEquals(6, options.threads());
        assertEquals(4, options.batchThreads());
        assertEquals(0, options.gpuLayers());
        assertTrue(options.swaFull());
        assertFalse(options.useMmap());
        assertTrue(options.useMlock());
    }

    @Test
    void prewarmHonorsConfiguredMinimumContextAndCapsItAtMaximum() {
        GgufLlamaCppRunOptions options = GgufLlamaCppRunOptions.resolve(
                "metal",
                "hi",
                2,
                true,
                4,
                configuration(Map.of(
                        "aqli.gguf.fast_run.max_auto_context", "2048",
                        "aqli.gguf.fast_run.prewarm_min_context", "4096")));

        assertEquals(2048, options.context());
    }

    @Test
    void clampsBatchAndThreadOptionsToPositiveValues() {
        GgufLlamaCppRunOptions options = GgufLlamaCppRunOptions.resolve(
                "metal",
                "hi",
                2,
                false,
                4,
                configuration(Map.of(
                        "aqli.gguf.fast_run.context", "128",
                        "aqli.gguf.fast_run.batch", "0",
                        "aqli.gguf.fast_run.ubatch", "-4",
                        "aqli.gguf.fast_run.threads", "0",
                        "aqli.gguf.fast_run.batch_threads", "-2")));

        assertEquals(1, options.batch());
        assertEquals(1, options.microBatch());
        assertEquals(1, options.threads());
        assertEquals(1, options.batchThreads());
    }

    @Test
    void cpuFallbackChangesOnlyGpuLayerSelection() {
        GgufLlamaCppRunOptions metal = GgufLlamaCppRunOptions.resolve(
                "metal", "hello", 32, false, 8, configuration(Map.of()));
        GgufLlamaCppRunOptions cpu = metal.withGpuLayers(0);

        assertEquals(0, cpu.gpuLayers());
        assertEquals(metal.context(), cpu.context());
        assertEquals(metal.batch(), cpu.batch());
        assertEquals(metal.microBatch(), cpu.microBatch());
        assertEquals(metal.threads(), cpu.threads());
        assertEquals(metal.batchThreads(), cpu.batchThreads());
        assertEquals(metal.swaFull(), cpu.swaFull());
        assertEquals(metal.useMmap(), cpu.useMmap());
        assertEquals(metal.useMlock(), cpu.useMlock());
    }

    private static GgufStandaloneConfiguration configuration(Map<String, String> values) {
        return new GgufStandaloneConfiguration(values::get, ignored -> null);
    }
}

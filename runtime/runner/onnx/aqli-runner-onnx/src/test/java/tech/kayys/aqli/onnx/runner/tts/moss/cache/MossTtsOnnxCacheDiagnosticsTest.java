package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MossTtsOnnxCacheDiagnosticsTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsGraphsUnavailableWhenCompanionCodecIsMissing() {
        var settings = settings(tempDir.resolve("cache"));
        var snapshot = MossTtsOnnxCacheDiagnostics.inspect(
                tempDir.resolve("model"), Optional.empty(), settings);

        assertFalse(snapshot.graphsAvailable());
        assertEquals(0, snapshot.graphs().size());
        assertFalse(snapshot.cacheDirectory().exists());
    }

    @Test
    void collectsGraphWarmthSidecarsAndCurrentCacheInventory() throws Exception {
        Path modelDir = Files.createDirectories(tempDir.resolve("model"));
        Path codecDir = Files.createDirectories(tempDir.resolve("codec"));
        Path configuredCacheRoot = tempDir.resolve("cache");
        var cacheSettings = settings(configuredCacheRoot);
        Path cacheRoot = Files.createDirectories(cacheSettings.optimizedGraphCacheRoot());
        write(modelDir, "tts_browser_onnx_meta.json",
                """
                {"files":{"prefill":"prefill.onnx","decode_step":"decode.onnx","local_fixed_sampled_frame":"sampler.onnx"}}
                """);
        write(codecDir, "codec_browser_onnx_meta.json",
                """
                {"files":{"decode_full":"full.onnx","decode_step":"step.onnx"}}
                """);
        for (String file : new String[] {"prefill.onnx", "decode.onnx", "sampler.onnx"}) {
            write(modelDir, file, "onnx");
        }
        for (String file : new String[] {"full.onnx", "step.onnx"}) {
            write(codecDir, file, "onnx");
        }
        write(modelDir, "weights.data", "sidecar");

        Path warmGraph = modelDir.resolve("prefill.onnx");
        Path optimized = MossTtsOnnxGraphResolver.optimizedCachePath(warmGraph, cacheRoot);
        Files.writeString(optimized, "optimized");

        var snapshot = MossTtsOnnxCacheDiagnostics.inspect(
                modelDir, Optional.of(codecDir), cacheSettings);

        assertTrue(snapshot.graphsAvailable());
        assertEquals(5, snapshot.graphs().size());
        assertEquals(1, snapshot.warmGraphCount());
        assertEquals(1, snapshot.graphs().get(0).externalDataSidecars());
        assertEquals(1, snapshot.graphs().get(1).externalDataSidecars());
        assertEquals(1, snapshot.graphs().get(2).externalDataSidecars());
        assertEquals(0, snapshot.graphs().get(3).externalDataSidecars());
        assertEquals(0, snapshot.graphs().get(4).externalDataSidecars());
        assertEquals(1, snapshot.cacheDirectory().currentOptimizedGraphFiles());
    }

    @Test
    void graphResolutionErrorsAreReportedWithoutPartialGraphResults() throws Exception {
        Path modelDir = Files.createDirectories(tempDir.resolve("broken-model"));
        Path codecDir = Files.createDirectories(tempDir.resolve("broken-codec"));
        write(modelDir, "tts_browser_onnx_meta.json", "{}");
        // Missing codec metadata deliberately makes graph discovery fail.

        var snapshot = MossTtsOnnxCacheDiagnostics.inspect(
                modelDir, Optional.of(codecDir), settings(tempDir.resolve("cache")));

        assertFalse(snapshot.graphsAvailable());
        assertTrue(snapshot.graphs().isEmpty());
        assertTrue(snapshot.graphError() != null && !snapshot.graphError().isBlank());
    }

    private MossTtsCacheSettings settings(Path cacheRoot) {
        return MossTtsCacheSettings.from(
                Map.of("aqli.onnx.optimized-model-cache.dir", cacheRoot.toString()),
                Map.of(),
                tempDir.toString());
    }

    private static void write(Path directory, String name, String content) throws Exception {
        Files.writeString(directory.resolve(name), content);
    }
}

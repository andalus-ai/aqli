package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tech.kayys.aqli.onnx.runner.tts.moss.MossTtsOnnxGraphResolver;

class MossTtsOptimizedCacheMaintenanceTest {

    @TempDir
    Path tempDir;

    @Test
    void dryRunAndApplyOnlyRemoveStaleGraphsForTheSelectedModel() throws Exception {
        Path modelDir = Files.createDirectories(tempDir.resolve("tts"));
        Path codecDir = Files.createDirectories(tempDir.resolve("codec"));
        Files.writeString(modelDir.resolve("tts_browser_onnx_meta.json"), "{\"files\":{}}");
        Files.writeString(codecDir.resolve("codec_browser_onnx_meta.json"), "{\"files\":{}}");

        List<MossTtsOnnxGraphResolver.Graph> graphs =
                MossTtsOnnxGraphResolver.resolveGraphs(modelDir, codecDir);
        for (MossTtsOnnxGraphResolver.Graph graph : graphs) {
            Files.createDirectories(graph.path().getParent());
            Files.writeString(graph.path(), "onnx");
        }

        Path cacheRoot = tempDir.resolve("cache");
        Path currentGraph = MossTtsOnnxGraphResolver.optimizedCachePath(graphs.get(0).path(), cacheRoot);
        Files.createDirectories(currentGraph.getParent());
        Files.writeString(currentGraph, "current");
        Path staleGraph = currentGraph.resolveSibling(
                MossTtsOnnxGraphResolver.cacheFilenamePrefix(graphs.get(0).path()) + "stale.optimized.onnx");
        Files.writeString(staleGraph, "stale-cache");

        MossTtsOptimizedCacheMaintenance maintenance = new MossTtsOptimizedCacheMaintenance();
        var preview = maintenance.prune(modelDir, codecDir, cacheRoot, true);

        assertEquals(0, preview.errors());
        assertEquals(1, preview.operations().size());
        assertEquals(Files.size(staleGraph), preview.affectedBytes());
        assertTrue(staleGraph.toFile().exists());
        assertEquals(preview.before(), preview.after());

        var applied = maintenance.prune(modelDir, codecDir, cacheRoot, false);

        assertEquals(0, applied.errors());
        assertEquals(1, applied.operations().size());
        assertTrue(applied.operations().get(0).deleted());
        assertFalse(Files.exists(staleGraph));
        assertTrue(Files.exists(currentGraph));
        assertEquals(0, applied.after().staleOptimizedGraphFiles());
    }
}

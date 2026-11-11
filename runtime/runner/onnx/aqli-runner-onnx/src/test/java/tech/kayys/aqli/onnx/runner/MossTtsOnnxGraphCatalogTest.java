package tech.kayys.aqli.onnx.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MossTtsOnnxGraphCatalogTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesGraphSetFromTtsAndCodecManifests() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("tts"));
        Path codecDir = Files.createDirectory(tempDir.resolve("codec"));
        Files.writeString(modelDir.resolve("tts_browser_onnx_meta.json"),
                """
                {"files":{"prefill":"prefill.onnx","decode_step":"decode.onnx",
                  "local_fixed_sampled_frame":"sample.onnx"}}
                """);
        Files.writeString(codecDir.resolve("codec_browser_onnx_meta.json"),
                """
                {"files":{"decode_full":"full.onnx","decode_step":"step.onnx"}}
                """);

        List<MossTtsOnnxGraphCatalog.Graph> graphs =
                MossTtsOnnxGraphCatalog.resolveGraphs(modelDir, codecDir);

        assertEquals(5, graphs.size());
        assertEquals(List.of("prefill.onnx", "decode.onnx", "sample.onnx", "full.onnx", "step.onnx"),
                graphs.stream().map(graph -> graph.path().getFileName().toString()).toList());
    }

    @Test
    void discoversExternalDataSidecarsInStableOrder() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("model"));
        Path graph = Files.writeString(modelDir.resolve("graph.onnx"), "graph");
        Files.writeString(modelDir.resolve("z.data"), "z");
        Files.writeString(modelDir.resolve("a.data"), "a");

        assertEquals(List.of("a.data", "z.data"),
                MossTtsOnnxGraphCatalog.externalDataSidecars(graph).stream()
                        .map(path -> path.getFileName().toString())
                        .toList());
    }

    @Test
    void optimizedCacheIdentityIncludesGraphAndSidecarMetadata() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("model"));
        Path cacheDir = Files.createDirectory(tempDir.resolve("cache"));
        Path graph = Files.writeString(modelDir.resolve("prefill.onnx"), "graph-v1");
        Path first = MossTtsOnnxGraphCatalog.optimizedCachePath(graph, cacheDir);
        Files.writeString(modelDir.resolve("weights.data"), "weights");
        Path second = MossTtsOnnxGraphCatalog.optimizedCachePath(graph, cacheDir);

        assertTrue(first.getFileName().toString().startsWith("prefill-"));
        assertTrue(first.getFileName().toString().endsWith(".optimized.onnx"));
        assertTrue(!first.equals(second));
    }
}

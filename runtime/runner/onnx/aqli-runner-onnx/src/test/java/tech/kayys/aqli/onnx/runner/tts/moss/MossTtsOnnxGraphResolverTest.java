package tech.kayys.aqli.onnx.runner.tts.moss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MossTtsOnnxGraphResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesTheThreeTtsAndTwoCodecGraphsUsingMetadataOverrides() throws Exception {
        Path model = Files.createDirectory(tempDir.resolve("tts"));
        Path codec = Files.createDirectory(tempDir.resolve("codec"));
        Files.writeString(model.resolve("tts_browser_onnx_meta.json"),
                """
                {"files":{"prefill":"custom-prefill.onnx","decode_step":"custom-decode.onnx",
                "local_fixed_sampled_frame":"custom-sampler.onnx"}}
                """);
        Files.writeString(codec.resolve("codec_browser_onnx_meta.json"),
                """
                {"files":{"decode_full":"custom-full.onnx","decode_step":"custom-step.onnx"}}
                """);

        var graphs = MossTtsOnnxGraphResolver.resolveGraphs(model, codec);

        assertEquals(5, graphs.size());
        assertEquals("tts prefill", graphs.get(0).label());
        assertEquals(model.resolve("custom-prefill.onnx"), graphs.get(0).path());
        assertEquals(codec.resolve("custom-full.onnx"), graphs.get(3).path());
        assertEquals(codec.resolve("custom-step.onnx"), graphs.get(4).path());
    }

    @Test
    void usesDefaultGraphFilenamesWhenMetadataDoesNotOverrideThem() throws Exception {
        Path model = Files.createDirectory(tempDir.resolve("tts"));
        Path codec = Files.createDirectory(tempDir.resolve("codec"));
        Files.writeString(model.resolve("tts_browser_onnx_meta.json"), "{}");
        Files.writeString(codec.resolve("codec_browser_onnx_meta.json"), "{}");

        var graphs = MossTtsOnnxGraphResolver.resolveGraphs(model, codec);

        assertEquals(model.resolve("moss_tts_prefill.onnx"), graphs.get(0).path());
        assertEquals(model.resolve("moss_tts_decode_step.onnx"), graphs.get(1).path());
        assertEquals(model.resolve("moss_tts_local_fixed_sampled_frame.onnx"), graphs.get(2).path());
        assertEquals(codec.resolve("moss_audio_tokenizer_decode_full.onnx"), graphs.get(3).path());
        assertEquals(codec.resolve("moss_audio_tokenizer_decode_step.onnx"), graphs.get(4).path());
    }

    @Test
    void cacheIdentityChangesWhenGraphOrExternalDataChanges() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Path cache = tempDir.resolve("cache");
        Path graph = Files.writeString(source.resolve("model.onnx"), "graph-v1");
        Path sidecar = Files.writeString(source.resolve("weights.data"), "weights-v1");

        Path first = MossTtsOnnxGraphResolver.optimizedCachePath(graph, cache);
        Files.writeString(sidecar, "weights-v2-expanded");
        Path second = MossTtsOnnxGraphResolver.optimizedCachePath(graph, cache);

        assertNotEquals(first.getFileName(), second.getFileName());
        Files.writeString(graph, "graph-v2");
        Path third = MossTtsOnnxGraphResolver.optimizedCachePath(graph, cache);
        assertNotEquals(second.getFileName(), third.getFileName());
        assertTrue(third.getFileName().toString().startsWith("model-"));
    }

    @Test
    void preResolvedSidecarsProduceTheSameOptimizedCacheIdentity() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("pre-resolved"));
        Path graph = Files.writeString(source.resolve("model.onnx"), "graph");
        Files.writeString(source.resolve("z.data"), "z");
        Files.writeString(source.resolve("a.data"), "a");
        Path cache = tempDir.resolve("cache");
        var sidecars = MossTtsOnnxGraphResolver.externalDataSidecars(graph);

        Path discovered = MossTtsOnnxGraphResolver.optimizedCachePath(graph, cache);
        Path reused = MossTtsOnnxGraphResolver.optimizedCachePath(graph, cache, sidecars);

        assertEquals(discovered, reused);
    }

    @Test
    void discoversExternalDataSidecarsInStableFilenameOrder() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Path graph = Files.writeString(source.resolve("model.onnx"), "graph");
        Files.writeString(source.resolve("z.data"), "z");
        Files.writeString(source.resolve("a.data"), "a");
        Files.writeString(source.resolve("ignored.bin"), "ignored");

        var sidecars = MossTtsOnnxGraphResolver.externalDataSidecars(graph);

        assertEquals(2, sidecars.size());
        assertEquals("a.data", sidecars.get(0).getFileName().toString());
        assertEquals("z.data", sidecars.get(1).getFileName().toString());
    }
}

package tech.kayys.aqli.provider.litert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LiteRTModelIndexResolverTest {
    @TempDir Path tempDir;

    @Test
    void resolvesIndexedLiteRtArtifactByModelId() throws Exception {
        Path model = Files.writeString(tempDir.resolve("gemma.litertlm"), "model");
        Path index = writeIndex("""
                [{"id":"google/gemma-4-E2B-it","shortId":"7c51c9","name":"Gemma 4","architecture":"gemma4","format":"litertlm","path":"%s"}]
                """.formatted(model));
        var result = new LiteRTModelIndexResolver().resolve(index, "7c51c9", false).orElseThrow();
        assertEquals(model, result.path());
        assertEquals("fast-index-litert", result.source());
    }

    @Test
    void resolvesCompatibleGemmaLiteRtEquivalentOnlyWhenEnabled() throws Exception {
        Path safetensors = Files.writeString(tempDir.resolve("model.safetensors"), "weights");
        Path litert = Files.writeString(tempDir.resolve("gemma-4-E2B-it.litertlm"), "model");
        Path index = writeIndex("""
                [
                  {"id":"google/gemma-4-E2B-it-qat-mobile-transformers","shortId":"0576e9","name":"Gemma 4 Mobile QAT","architecture":"gemma4","format":"safetensors","path":"%s"},
                  {"id":"google/gemma-4-E2B-it-litertlm","name":"Gemma 4 LiteRT","architecture":"gemma4","format":"litertlm","path":"%s"}
                ]
                """.formatted(safetensors, litert));
        var resolver = new LiteRTModelIndexResolver();
        assertTrue(resolver.resolve(index, "0576e9", false).isEmpty());
        var result = resolver.resolve(index, "0576e9", true).orElseThrow();
        assertEquals(litert, result.path());
        assertEquals("fast-index-equivalent-litert", result.source());
    }

    @Test
    void malformedOrMissingIndexFallsBackCleanly() throws Exception {
        Path index = Files.writeString(tempDir.resolve("index.json"), "{not-json");
        assertTrue(new LiteRTModelIndexResolver().resolve(index, "gemma", true).isEmpty());
        assertTrue(new LiteRTModelIndexResolver().resolve(tempDir.resolve("missing.json"), "gemma", true).isEmpty());
    }

    private Path writeIndex(String json) throws Exception {
        return Files.writeString(tempDir.resolve("index.json"), json);
    }
}

package tech.kayys.aqli.provider.litert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LiteRTRouteResolverTest {
    @TempDir Path tempDir;

    @Test
    void resolvesExplicitLiteRtLmFile() throws Exception {
        Path model = Files.writeString(tempDir.resolve("model.litertlm"), "model");
        var result = new LiteRTRouteResolver().resolve(model.toString(), null, null, false).orElseThrow();
        assertEquals(model, result.path());
        assertEquals("modelFile", result.source());
        assertFalse(result.cacheHit());
    }

    @Test
    void selectsPreferredLiteRtLmArtifactFromDirectory() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("model"));
        Files.writeString(directory.resolve("model-qualcomm.litertlm"), "q");
        Path preferred = Files.writeString(directory.resolve("gemma-4-e2b-it.litertlm"), "model");
        var result = new LiteRTRouteResolver().resolve(null, directory.toString(), null, false).orElseThrow();
        assertEquals(preferred, result.path());
        assertEquals("model", result.source());
    }

    @Test
    void resolvesIndexedLiteRtArtifact() throws Exception {
        Path model = Files.writeString(tempDir.resolve("indexed.litertlm"), "model");
        Path index = Files.writeString(tempDir.resolve("index.json"), """
                [{"id":"google/gemma-4-E2B-it","shortId":"abc123","format":"litertlm","path":"%s"}]
                """.formatted(model));
        var result = new LiteRTRouteResolver().resolve(null, "abc123", index, false).orElseThrow();
        assertEquals(model, result.path());
        assertEquals("fast-index-litert", result.source());
        assertTrue(result.cacheHit());
    }


    @Test
    void resolvesIndexedArtifactBesideStaleIndexedDirectoryPath() throws Exception {
        Path artifact = Files.writeString(tempDir.resolve("gemma.litertlm"), "model");
        Path staleDirectory = tempDir.resolve("model-directory");
        Path index = Files.writeString(tempDir.resolve("index.json"), """
                [{"id":"google/gemma-4-E2B-it","format":"litertlm","path":"%s"}]
                """.formatted(staleDirectory));
        var result = new LiteRTRouteResolver().resolve(null, "google/gemma-4-E2B-it", index, false).orElseThrow();
        assertEquals(artifact, result.path());
    }

    @Test
    void ignoresNonLiteRtExplicitFileAndUnresolvedIndex() throws Exception {
        Path other = Files.writeString(tempDir.resolve("weights.safetensors"), "weights");
        Files.writeString(tempDir.resolve("unrelated.litertlm"), "model");
        assertTrue(new LiteRTRouteResolver().resolve(other.toString(), null, null, false).isEmpty());
        assertTrue(new LiteRTRouteResolver().resolve(tempDir.resolve("missing-model").toString(), null, null, false).isEmpty());
        assertTrue(new LiteRTRouteResolver().resolve(null, "missing", tempDir.resolve("missing.json"), false).isEmpty());
    }
}

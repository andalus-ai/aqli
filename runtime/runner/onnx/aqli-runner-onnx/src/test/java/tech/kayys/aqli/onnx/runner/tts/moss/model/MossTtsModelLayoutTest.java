package tech.kayys.aqli.onnx.runner.tts.moss.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MossTtsModelLayoutTest {
    @TempDir
    Path tempDir;

    @Test
    void recognizesACompleteMossTtsDirectory() throws Exception {
        Path model = Files.createDirectory(tempDir.resolve("model"));
        Files.writeString(model.resolve("browser_poc_manifest.json"), "{}");
        Files.writeString(model.resolve("tts_browser_onnx_meta.json"), "{}");

        assertTrue(MossTtsModelLayout.isModelDirectory(model));
        assertEquals(model, MossTtsModelLayout.directoryFrom(model).orElseThrow());
    }

    @Test
    void resolvesModelDirectoryFromAFileOrNestedPathParent() throws Exception {
        Path model = Files.createDirectory(tempDir.resolve("model"));
        Files.writeString(model.resolve("browser_poc_manifest.json"), "{}");
        Files.writeString(model.resolve("tts_browser_onnx_meta.json"), "{}");
        Path graph = Files.writeString(model.resolve("model.onnx"), "onnx");

        assertEquals(model, MossTtsModelLayout.directoryFrom(graph).orElseThrow());
    }

    @Test
    void prefersResolvedLocalPathThenFallsBackToModelReference() throws Exception {
        Path model = Files.createDirectory(tempDir.resolve("model"));
        Files.writeString(model.resolve("browser_poc_manifest.json"), "{}");
        Files.writeString(model.resolve("tts_browser_onnx_meta.json"), "{}");

        assertEquals(model, MossTtsModelLayout.resolveDirectory("invalid path \\0", model.toString()).orElseThrow());
        assertEquals(model, MossTtsModelLayout.resolveDirectory(null, model.toString()).orElseThrow());
    }

    @Test
    void rejectsIncompleteDirectoriesAndUnrelatedPaths() throws Exception {
        Path model = Files.createDirectory(tempDir.resolve("incomplete"));
        Files.writeString(model.resolve("browser_poc_manifest.json"), "{}");

        assertFalse(MossTtsModelLayout.isModelDirectory(model));
        assertTrue(MossTtsModelLayout.directoryFrom(model).isEmpty());
        assertTrue(MossTtsModelLayout.resolveDirectory(null, tempDir.resolve("missing").toString()).isEmpty());
    }
}

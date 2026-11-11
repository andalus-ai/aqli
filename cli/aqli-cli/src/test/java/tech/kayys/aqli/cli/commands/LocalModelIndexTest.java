package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.aqli.cli.util.CLIUtils;

class LocalModelIndexTest {

    @TempDir
    Path tempDir;

    private String previousAqliHome;

    @BeforeEach
    void setUp() {
        previousAqliHome = System.getProperty("aqli.home");
        System.setProperty("aqli.home", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (previousAqliHome == null) {
            System.clearProperty("aqli.home");
        } else {
            System.setProperty("aqli.home", previousAqliHome);
        }
    }

    @Test
    void refreshDiscoversOnnxRepositoryInLegacySafetensorsDirectory() throws Exception {
        Path repoDir = tempDir.resolve("models")
                .resolve("safetensors")
                .resolve("OpenMOSS-Team")
                .resolve("MOSS-TTS-Nano-100M-ONNX");
        Files.createDirectories(repoDir);
        Files.write(repoDir.resolve("model.onnx"), new byte[] { 1, 2, 3 });
        Files.writeString(repoDir.resolve("tts_browser_onnx_meta.json"), "{\"checkpoint_path\":\"MOSS-TTS-Nano\"}");

        List<LocalModelIndex.Entry> entries = LocalModelIndex.refreshFromDisk();

        LocalModelIndex.Entry entry = entries.stream()
                .filter(e -> "OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX".equals(e.id))
                .findFirst()
                .orElseThrow();

        assertEquals("MOSS-TTS-Nano-100M-ONNX", entry.name);
        assertEquals("onnx", entry.format);
        assertEquals("moss-tts", entry.architecture);
        assertTrue(entry.runnable);
    }

    @Test
    void refreshNormalizesSemanticManifestShortIdsToStableHash() throws Exception {
        Path blobDir = tempDir.resolve("models")
                .resolve("blobs")
                .resolve("litert-community")
                .resolve("resnet50")
                .resolve("litert-community__resnet50");
        Files.createDirectories(blobDir);
        Files.write(blobDir.resolve("model.tflite"), new byte[] { 1, 2, 3 });

        Path manifestDir = tempDir.resolve("models").resolve("manifests");
        Files.createDirectories(manifestDir);
        Files.writeString(manifestDir.resolve("litert-community__resnet50.json"), """
                {
                  "id": "litert-community__resnet50",
                  "modelId": "litert-community/resnet50",
                  "shortId": "litert",
                  "name": "resnet50",
                  "format": "litert",
                  "blobPath": "%s",
                  "source": "huggingface"
                }
                """.formatted(blobDir.toAbsolutePath()));

        List<LocalModelIndex.Entry> entries = LocalModelIndex.refreshFromDisk();

        LocalModelIndex.Entry entry = entries.stream()
                .filter(e -> "litert-community/resnet50".equals(e.id))
                .findFirst()
                .orElseThrow();

        assertEquals(CLIUtils.generateShortId("litert-community/resnet50"), entry.shortId);
        assertTrue(entry.shortId.matches("[0-9a-f]{6}"));
    }

    @Test
    void findAcceptsLegacyRepositoryFileShortId() throws Exception {
        Path repoDir = tempDir.resolve("models")
                .resolve("safetensors")
                .resolve("google")
                .resolve("functiongemma-270m-it");
        Files.createDirectories(repoDir);
        Files.write(repoDir.resolve("model.safetensors"), new byte[] { 1, 2, 3 });
        Files.writeString(repoDir.resolve("config.json"), """
                {
                  "architectures": ["Gemma3ForCausalLM"],
                  "model_type": "gemma3_text"
                }
                """);

        LocalModelIndex.refreshFromDisk();

        String legacyShortId = CLIUtils.generateShortId("google/functiongemma-270m-it/model.safetensors");
        LocalModelIndex.Entry entry = LocalModelIndex.find(legacyShortId).orElseThrow();

        assertEquals("google/functiongemma-270m-it", entry.id);
        assertEquals("functiongemma-270m-it", entry.name);
    }
}

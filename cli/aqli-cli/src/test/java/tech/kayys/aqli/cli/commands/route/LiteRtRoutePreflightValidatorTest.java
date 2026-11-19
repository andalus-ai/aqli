package tech.kayys.aqli.cli.commands.route;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteRtRoutePreflightValidatorTest {

    private static final String EXPERIMENTAL_TASK_RUNNER =
            "aqli.litert.enable_experimental_gemma4_task_runner";
    private static final String EXPERIMENTAL_RAW_LITERTLM =
            "aqli.litert.enable_experimental_raw_litertlm";

    @TempDir
    Path tempDir;

    @Test
    void reportsUnsupportedGemma4TaskArtifact() throws Exception {
        Path artifact = tempDir.resolve("gemma-4-export.task");
        Files.createFile(artifact);

        String diagnostic = LiteRtRoutePreflightValidator.detectUnsupported("litert", artifact.toString());

        assertNotNull(diagnostic);
        assertTrue(diagnostic.contains("Gemma 4 LiteRT runner is disabled"));
    }

    @Test
    void allowsNativeGemma4LiteRtLmArtifact() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("model"));
        Files.createFile(directory.resolve("gemma-4-native.litertlm"));
        Files.createFile(directory.resolve("gemma-4-export.task"));

        assertNull(LiteRtRoutePreflightValidator.detectUnsupported("litert", directory.toString()));
    }

    @Test
    void ignoresOtherProvidersAndNonGemmaArtifacts() throws Exception {
        Path artifact = tempDir.resolve("other-model.task");
        Files.createFile(artifact);

        assertNull(LiteRtRoutePreflightValidator.detectUnsupported("cpu", artifact.toString()));
        assertNull(LiteRtRoutePreflightValidator.detectUnsupported("litert", artifact.toString()));
    }

    @Test
    void ignoresMissingOrBlankPaths() {
        assertNull(LiteRtRoutePreflightValidator.detectUnsupported("litert", null));
        assertNull(LiteRtRoutePreflightValidator.detectUnsupported("litert", " "));
        assertNull(LiteRtRoutePreflightValidator.detectUnsupported("litert", tempDir.resolve("missing").toString()));
    }

    @Test
    void experimentalPropertiesBypassTheGuard() throws Exception {
        Path artifact = tempDir.resolve("gemma4-export.tflite");
        Files.createFile(artifact);
        String oldTask = System.getProperty(EXPERIMENTAL_TASK_RUNNER);
        String oldRaw = System.getProperty(EXPERIMENTAL_RAW_LITERTLM);
        try {
            System.setProperty(EXPERIMENTAL_TASK_RUNNER, "true");
            assertNull(LiteRtRoutePreflightValidator.detectUnsupported("litert", artifact.toString()));
            System.clearProperty(EXPERIMENTAL_TASK_RUNNER);
            System.setProperty(EXPERIMENTAL_RAW_LITERTLM, "true");
            assertNull(LiteRtRoutePreflightValidator.detectUnsupported("litert", artifact.toString()));
        } finally {
            restore(EXPERIMENTAL_TASK_RUNNER, oldTask);
            restore(EXPERIMENTAL_RAW_LITERTLM, oldRaw);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}

package tech.kayys.aqli.cli.commands.route;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetensorCompatibilityPreflightValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void identifiesSafetensorCheckpointDirectory() throws Exception {
        Path checkpoint = checkpoint("{}", "model.safetensors");

        assertTrue(SafetensorCompatibilityPreflightValidator.isSafetensorCheckpointDir(checkpoint.toString()));
        assertFalse(SafetensorCompatibilityPreflightValidator.isSafetensorCheckpointDir(tempDir.toString()));
        assertFalse(SafetensorCompatibilityPreflightValidator.isSafetensorCheckpointDir(null));
        assertFalse(SafetensorCompatibilityPreflightValidator.isSafetensorCheckpointDir(" "));
    }


    @Test
    void identifiesIndividualSafetensorWeightFiles() throws Exception {
        Path weights = Files.createFile(tempDir.resolve("model.safetensors"));
        Path singularWeights = Files.createFile(tempDir.resolve("model.safetensor"));
        Path unrelated = Files.createFile(tempDir.resolve("model.bin"));
        Path directory = Files.createDirectory(tempDir.resolve("weights-dir"));

        assertTrue(SafetensorCompatibilityPreflightValidator.isSafetensorWeightFile(weights.toString()));
        assertTrue(SafetensorCompatibilityPreflightValidator.isSafetensorWeightFile(singularWeights.toString()));
        assertFalse(SafetensorCompatibilityPreflightValidator.isSafetensorWeightFile(unrelated.toString()));
        assertFalse(SafetensorCompatibilityPreflightValidator.isSafetensorWeightFile(directory.toString()));
        assertFalse(SafetensorCompatibilityPreflightValidator.isSafetensorWeightFile(null));
        assertFalse(SafetensorCompatibilityPreflightValidator.isSafetensorWeightFile(" "));
    }

    @Test
    void reportsGemma4UnifiedWrapperWithoutTextConfig() throws Exception {
        Path checkpoint = checkpoint(
                "not-json: \"model_type\": \"gemma4_unified\", \"vision_config\": {}",
                "model.safetensors");

        String diagnostic = SafetensorCompatibilityPreflightValidator.detect("safetensor", checkpoint.toString());

        assertNotNull(diagnostic);
        assertTrue(diagnostic.contains("unified multimodal embedder path"));
    }

    @Test
    void acceptsGemma4UnifiedWrapperWithTextConfig() throws Exception {
        Path checkpoint = checkpoint(
                "not-json: \"model_type\": \"gemma4_unified\", \"vision_config\": {}, \"text_config\": {}",
                "model.safetensors");

        assertNull(SafetensorCompatibilityPreflightValidator.detect("safetensor", checkpoint.toString()));
    }

    @Test
    void reportsGemma4ConditionalMultimodalWrapperWithoutTextConfig() throws Exception {
        Path checkpoint = checkpoint(
                "not-json: \"Gemma4ForConditionalGeneration\", \"vision_config\": {}",
                "model.safetensors");

        String diagnostic = SafetensorCompatibilityPreflightValidator.detect("safetensor", checkpoint.toString());

        assertNotNull(diagnostic);
        assertTrue(diagnostic.contains("does not reliably support Gemma4 multimodal text checkpoints"));
    }

    @Test
    void ignoresOtherProvidersAndNonCheckpointDirectories() throws Exception {
        Path checkpoint = checkpoint(
                "{\"model_type\": \"gemma4_unified\", \"vision_config\": {}}",
                "model.safetensors");

        assertNull(SafetensorCompatibilityPreflightValidator.detect("cpu", checkpoint.toString()));
        assertNull(SafetensorCompatibilityPreflightValidator.detect("safetensor", tempDir.toString()));
    }

    private Path checkpoint(String config, String weights) throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("checkpoint-" + System.nanoTime()));
        Files.writeString(directory.resolve("config.json"), config);
        Files.createFile(directory.resolve(weights));
        return directory;
    }
}

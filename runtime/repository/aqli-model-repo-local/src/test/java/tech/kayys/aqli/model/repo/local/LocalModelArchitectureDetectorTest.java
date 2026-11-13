package tech.kayys.aqli.model.repo.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalModelArchitectureDetectorTest {

    @TempDir
    Path temporaryDirectory;

    private final LocalModelArchitectureDetector detector = new LocalModelArchitectureDetector();

    @Test
    void prefersFirstDeclaredArchitecture() throws Exception {
        Files.writeString(temporaryDirectory.resolve("config.json"), """
                {
                  "architectures": ["Gemma4ForConditionalGeneration", "OtherArchitecture"],
                  "model_type": "gemma4"
                }
                """);

        assertEquals("Gemma4ForConditionalGeneration", detector.detect(temporaryDirectory).orElseThrow());
    }

    @Test
    void fallsBackToModelTypeWhenArchitecturesAreAbsent() throws Exception {
        Files.writeString(temporaryDirectory.resolve("config.json"), """
                { "model_type": "mistral" }
                """);

        assertEquals("mistral", detector.detect(temporaryDirectory).orElseThrow());
    }

    @Test
    void returnsEmptyForMissingOrMalformedConfig() throws Exception {
        assertTrue(detector.detect(temporaryDirectory).isEmpty());
        Files.writeString(temporaryDirectory.resolve("config.json"), "{ malformed");
        assertTrue(detector.detect(temporaryDirectory).isEmpty());
    }

    @Test
    void ignoresBlankArchitectureValuesAndUsesModelType() throws Exception {
        Files.writeString(temporaryDirectory.resolve("config.json"), """
                { "architectures": [""], "model_type": "qwen2" }
                """);

        assertEquals("qwen2", detector.detect(temporaryDirectory).orElseThrow());
    }
}

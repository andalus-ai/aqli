package tech.kayys.aqli.cli.commands.features.scaffold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldContentGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesPipelineProjectDeterministicallyWithoutFilesystemWrites() {
        Path root = tempDir.resolve("demo-pipeline");
        var generator = new ScaffoldContentGenerator("1.2.3");

        var first = generator.generate(pipeline(root));
        var second = generator.generate(pipeline(root));

        assertEquals(7, first.size());
        assertEquals(first, second);
        assertFalse(Files.exists(root));

        var javaSource = first.stream()
                .filter(file -> file.relativePath().endsWith("DemoPipeline.java"))
                .findFirst()
                .orElseThrow();
        assertTrue(javaSource.content().contains("public class DemoPipeline implements ModelPipeline"));
        var manifest = first.stream()
                .filter(file -> file.relativePath().endsWith("aqli-extension.json"))
                .findFirst()
                .orElseThrow();
        assertTrue(manifest.content().contains("1.2.3"));
    }

    @Test
    void generatesModelFamilyServiceAndPluginDescriptor() {
        Path root = tempDir.resolve("demo-model");
        var files = new ScaffoldContentGenerator("1.2.3").generate(modelFamily(root));

        assertEquals(9, files.size());
        assertTrue(files.stream().anyMatch(file -> file.relativePath().endsWith(
                "META-INF/services/tech.kayys.aljabr.spi.model.ModelFamilyPlugin")));
        assertTrue(files.stream().anyMatch(file -> file.relativePath().endsWith(
                "META-INF/services/tech.kayys.aqli.spi.plugin.AqliPlugin")));
        assertTrue(files.stream().anyMatch(file -> file.relativePath().equals("lib/src/main/resources/plugin.json")));
        assertFalse(Files.exists(root));
    }

    private static ScaffoldContentGenerator.ScaffoldDefinition pipeline(Path root) {
        return new ScaffoldContentGenerator.ScaffoldDefinition(
                root, "demo-pipeline", "pipeline", "demo-pipeline", "demo-pipeline",
                "Demo Pipeline", "tech.example.demo", "DemoPipeline", "custom",
                List.of("TEXT"), List.of("TEXT"), List.of(), List.of(),
                List.of("custom", "external-extension"), "", List.of(), List.of(),
                List.of(), List.of(), List.of(), "", "", "", "optional", null);
    }

    private static ScaffoldContentGenerator.ScaffoldDefinition modelFamily(Path root) {
        return new ScaffoldContentGenerator.ScaffoldDefinition(
                root, "demo-model", "model-family", "demo-model", "",
                "Demo Model", "tech.example.demo", "DemoModelFamily", "demo-model",
                List.of(), List.of(), List.of(), List.of(),
                List.of("llm", "external-extension"), "", List.of(), List.of(),
                List.of("demo-model"), List.of("DemoForCausalLM"), List.of("TOKENIZER"),
                "hf-bpe", "ready", "", "optional", null);
    }
}

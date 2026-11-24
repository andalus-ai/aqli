package tech.kayys.aqli.cli.commands.features.scaffold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void dryRunReportsFilesWithoutCreatingTheTargetDirectory() throws Exception {
        Path root = tempDir.resolve("dry-run");
        var writer = new ScaffoldFileWriter(new ScaffoldContentGenerator("1.2.3"));

        var results = writer.write(pipeline(root), false, true);

        assertEquals(7, results.size());
        assertTrue(results.stream().allMatch(item -> "would-create".equals(item.get("status"))));
        assertFalse(Files.exists(root));
    }

    @Test
    void writesFilesAndRequiresForceToOverwriteExistingFiles() throws Exception {
        Path root = tempDir.resolve("write");
        var writer = new ScaffoldFileWriter(new ScaffoldContentGenerator("1.2.3"));

        var created = writer.write(pipeline(root), false, false);
        assertEquals(7, created.size());
        assertTrue(Files.exists(root.resolve("README.md")));
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> writer.write(pipeline(root), false, false));

        var overwritten = writer.write(pipeline(root), true, false);
        assertEquals(7, overwritten.size());
        assertTrue(overwritten.stream().allMatch(item -> "overwritten".equals(item.get("status"))));
    }

    private static ScaffoldContentGenerator.ScaffoldDefinition pipeline(Path root) {
        return new ScaffoldContentGenerator.ScaffoldDefinition(
                root, "demo-pipeline", "pipeline", "demo-pipeline", "demo-pipeline",
                "Demo Pipeline", "tech.example.demo", "DemoPipeline", "custom",
                List.of("TEXT"), List.of("TEXT"), List.of(), List.of(),
                List.of("custom", "external-extension"), "", List.of(), List.of(),
                List.of(), List.of(), List.of(), "", "", "", "optional", null);
    }
}

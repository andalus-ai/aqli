package tech.kayys.aqli.cli.commands.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageMetadataReplayCommandBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsReplayArgumentsInStableOrderAndOmitsUnsetValues() {
        var request = new ImageMetadataReplayCommandBuilder.Request(
                "hf:org/model", "native", "java", "draw a tree", "42",
                24, 7.5, 512, 256, "result.png");

        assertEquals(List.of(
                "aqli", "run",
                "--model", "hf:org/model",
                "--provider", "native",
                "--runner", "java",
                "--prompt", "draw a tree",
                "--seed", "42",
                "--steps", "24",
                "--guidance-scale", "7.5",
                "--width", "512",
                "--height", "256",
                "--output", "result.png"),
                ImageMetadataReplayCommandBuilder.build(request));
    }

    @Test
    void omitsUnsetAndBlankOptionalValues() {
        var request = new ImageMetadataReplayCommandBuilder.Request(
                "hf:org/model", null, null, " ", null,
                null, null, null, null, null);

        assertEquals(List.of("aqli", "run", "--model", "hf:org/model"),
                ImageMetadataReplayCommandBuilder.build(request));
    }

    @Test
    void resolvesLocalModelDirectoriesAndFilesToSpecificOptions() throws Exception {
        Path modelDirectory = Files.createDirectory(tempDir.resolve("model"));
        Path modelFile = Files.createFile(tempDir.resolve("model.gguf"));

        assertModelTarget(modelDirectory.toString(), "--modelDir");
        assertModelTarget(modelFile.toString(), "--modelFile");
    }

    private static void assertModelTarget(String model, String expectedFlag) {
        var request = new ImageMetadataReplayCommandBuilder.Request(
                model, null, null, null, null, null, null, null, null, null);
        List<String> args = ImageMetadataReplayCommandBuilder.build(request);
        assertEquals(expectedFlag, args.get(2));
        assertEquals(model, args.get(3));
    }
}

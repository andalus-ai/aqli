package tech.kayys.aqli.cli.commands.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import tech.kayys.aqli.cli.spi.run.RunCapabilityRegistry;

/** Verifies parsing and typed snapshots for vision-input options. */
class VisionInputRunCapabilityTest {

    @Test
    void snapshotsVisionAndOcrOverridesThroughRegistry() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--vision-input", "first.png",
                "--input-images", "second.png,third.png",
                "--ocr",
                "--onnx-image-tokens", "64",
                "--onnx-ocr-raw");

        VisionInputRunOptions options = RunCapabilityRegistry.load(
                VisionInputRunCapabilityTest.class.getClassLoader())
                .snapshot("vision-input", commandLine.getParseResult(), VisionInputRunOptions.class)
                .orElseThrow();

        assertEquals("first.png", options.inputImagePath());
        assertEquals(List.of("second.png", "third.png"), options.inputImagePaths());
        assertTrue(options.ocrMode());
        assertEquals(64, options.ocrImageTokens());
        assertTrue(options.ocrRawOutput());
    }

    @Test
    void keepsVisionAndOcrOptionsEmptyByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        VisionInputRunOptions options = new VisionInputRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.inputImagePath());
        assertEquals(List.of(), options.inputImagePaths());
        assertFalse(options.ocrMode());
        assertNull(options.ocrImageTokens());
        assertFalse(options.ocrRawOutput());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new VisionInputRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

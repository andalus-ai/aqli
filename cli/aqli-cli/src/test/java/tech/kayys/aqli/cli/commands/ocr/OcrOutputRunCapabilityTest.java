package tech.kayys.aqli.cli.commands.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies parsing and typed snapshots for OCR output options. */
class OcrOutputRunCapabilityTest {

    @Test
    void snapshotsStructuredJsonAndOutputPathsIncludingAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--onnx-ocr-json",
                "--ocr-metadata-output", "result.json",
                "--ocr-boxes-output", "overlay.png");

        OcrOutputRunOptions options = new OcrOutputRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.structuredJson());
        assertEquals("result.json", options.jsonOutputPath());
        assertEquals("overlay.png", options.overlayOutputPath());
    }

    @Test
    void keepsOcrOutputOptionsDisabledByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        OcrOutputRunOptions options = new OcrOutputRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.structuredJson());
        assertNull(options.jsonOutputPath());
        assertNull(options.overlayOutputPath());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new OcrOutputRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

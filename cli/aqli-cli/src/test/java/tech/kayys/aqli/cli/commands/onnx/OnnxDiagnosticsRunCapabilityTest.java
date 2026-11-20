package tech.kayys.aqli.cli.commands.onnx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies parsing and typed snapshots for ONNX diagnostics options. */
class OnnxDiagnosticsRunCapabilityTest {

    @Test
    void snapshotsDiagnosticsAndProbeOptionsIncludingAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--onnx-preflight",
                "--ocr-vision-probe",
                "--ocr-pipeline-probe",
                "--ocr-probe-image-tokens", "0",
                "--ocr-probe-decode-tokens", "12");

        OnnxDiagnosticsRunOptions options =
                new OnnxDiagnosticsRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.diagnostics());
        assertTrue(options.visionProbe());
        assertTrue(options.pipelineProbe());
        assertEquals(0, options.imageTokens());
        assertEquals(12, options.decodeTokens());
    }

    @Test
    void keepsDiagnosticsOptionsDisabledByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        OnnxDiagnosticsRunOptions options =
                new OnnxDiagnosticsRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.diagnostics());
        assertFalse(options.visionProbe());
        assertFalse(options.pipelineProbe());
        assertNull(options.imageTokens());
        assertNull(options.decodeTokens());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new OnnxDiagnosticsRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

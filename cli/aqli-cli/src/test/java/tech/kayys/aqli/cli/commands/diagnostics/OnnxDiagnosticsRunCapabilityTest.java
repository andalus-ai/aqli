package tech.kayys.aqli.cli.commands.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies parsing and typed snapshots for ONNX diagnostic options. */
class OnnxDiagnosticsRunCapabilityTest {

    @Test
    void snapshotsProbeOptionsAndAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--onnx-preflight",
                "--ocr-vision-probe",
                "--ocr-pipeline-probe",
                "--ocr-probe-image-tokens", "128",
                "--ocr-probe-decode-tokens", "12");

        OnnxDiagnosticsRunOptions options =
                new OnnxDiagnosticsRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.diagnostics());
        assertTrue(options.visionProbe());
        assertTrue(options.pipelineProbe());
        assertEquals(128, options.probeImageTokens());
        assertEquals(12, options.probeDecodeTokens());
    }

    @Test
    void keepsDiagnosticsDisabledAndProbeLimitsUnsetByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        OnnxDiagnosticsRunOptions options =
                new OnnxDiagnosticsRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.diagnostics());
        assertFalse(options.visionProbe());
        assertFalse(options.pipelineProbe());
        assertNull(options.probeImageTokens());
        assertNull(options.probeDecodeTokens());
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

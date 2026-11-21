package tech.kayys.aqli.cli.commands.onnx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies ONNX graph-variant option parsing and typed snapshots. */
class OnnxModelVariantRunCapabilityTest {

    @Test
    void snapshotsExplicitVariant() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--onnx-variant", "q4");

        OnnxModelVariantRunOptions options =
                new OnnxModelVariantRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("q4", options.variant());
    }

    @Test
    void supportsPrecisionAlias() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--onnx-precision", "q8");

        OnnxModelVariantRunOptions options =
                new OnnxModelVariantRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("q8", options.variant());
    }

    @Test
    void leavesVariantUnsetByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        OnnxModelVariantRunOptions options =
                new OnnxModelVariantRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.variant());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new OnnxModelVariantRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

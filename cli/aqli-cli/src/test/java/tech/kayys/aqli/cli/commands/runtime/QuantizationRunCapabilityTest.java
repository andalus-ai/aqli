package tech.kayys.aqli.cli.commands.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies quantization option parsing and defaults. */
class QuantizationRunCapabilityTest {

    @Test
    void snapshotsInferenceAndKvCacheQuantization() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--quantize", "awq",
                "--quantize-bits", "8",
                "--quantize-kv", "int8");

        QuantizationRunOptions options =
                new QuantizationRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("awq", options.strategy());
        assertEquals(8, options.bits());
        assertEquals("int8", options.kvCacheMode());
    }

    @Test
    void snapshotsDefaults() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        QuantizationRunOptions options =
                new QuantizationRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.strategy());
        assertEquals(4, options.bits());
        assertEquals("none", options.kvCacheMode());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new QuantizationRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

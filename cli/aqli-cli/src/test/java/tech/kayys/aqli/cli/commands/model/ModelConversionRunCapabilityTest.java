package tech.kayys.aqli.cli.commands.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies conversion option snapshots and defaults. */
class ModelConversionRunCapabilityTest {

    @Test
    void snapshotsExplicitConversionOptions() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--convert-mode", "off",
                "--gguf-outtype", "f16",
                "--gguf-quant", "Q4_K_M",
                "--gguf");

        ModelConversionRunOptions options =
                new ModelConversionRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("off", options.convertMode());
        assertEquals("f16", options.ggufOutType());
        assertEquals("Q4_K_M", options.ggufQuant());
        org.junit.jupiter.api.Assertions.assertTrue(options.forceGguf());
    }

    @Test
    void defaultsConversionOptions() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ModelConversionRunOptions options =
                new ModelConversionRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("auto", options.convertMode());
        assertNull(options.ggufOutType());
        assertNull(options.ggufQuant());
        org.junit.jupiter.api.Assertions.assertFalse(options.forceGguf());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ModelConversionRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

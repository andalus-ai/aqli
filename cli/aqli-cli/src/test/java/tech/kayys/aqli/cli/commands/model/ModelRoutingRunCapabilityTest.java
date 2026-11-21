package tech.kayys.aqli.cli.commands.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies provider and model-format option snapshots. */
class ModelRoutingRunCapabilityTest {

    @Test
    void snapshotsExplicitProviderAndFormat() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--provider", "safetensor", "--format", "safetensors");

        ModelRoutingRunOptions options =
                new ModelRoutingRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("safetensor", options.providerId());
        assertEquals("safetensors", options.format());
    }

    @Test
    void providerMayBeSpecifiedWithoutAValueForAutoSelection() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--provider");

        ModelRoutingRunOptions options =
                new ModelRoutingRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("", options.providerId());
        assertNull(options.format());
    }

    @Test
    void defaultsToAutomaticProviderAndFormatSelection() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ModelRoutingRunOptions options =
                new ModelRoutingRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.providerId());
        assertNull(options.format());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ModelRoutingRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

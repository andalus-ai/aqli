package tech.kayys.aqli.cli.commands.presentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies run presentation flags and their defaults. */
class RunPresentationRunCapabilityTest {

    @Test
    void snapshotsPresentationFlags() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--no-banner", "--no-info", "--only-text", "--raw", "--debug-prompt");

        RunPresentationRunOptions options =
                new RunPresentationRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.noBanner());
        assertTrue(options.noInfo());
        assertTrue(options.onlyText());
        assertTrue(options.raw());
        assertTrue(options.debugPrompt());
    }

    @Test
    void defaultsPresentationFlagsToFalse() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        RunPresentationRunOptions options =
                new RunPresentationRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.noBanner());
        assertFalse(options.noInfo());
        assertFalse(options.onlyText());
        assertFalse(options.raw());
        assertFalse(options.debugPrompt());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new RunPresentationRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

package tech.kayys.aqli.cli.commands.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies model repository option aliases and defaults. */
class ModelRepositoryRunCapabilityTest {

    @Test
    void snapshotsRepositoryOptionsAndAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--local", "--branch", "release", "--import", "--copy", "--force");

        ModelRepositoryRunOptions options =
                new ModelRepositoryRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.offline());
        assertEquals("release", options.branch());
        assertTrue(options.importModel());
        assertTrue(options.copyModel());
        assertTrue(options.force());
    }

    @Test
    void defaultsRepositoryOptions() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ModelRepositoryRunOptions options =
                new ModelRepositoryRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.offline());
        assertEquals(null, options.branch());
        assertFalse(options.importModel());
        assertFalse(options.copyModel());
        assertFalse(options.force());
    }

    @Test
    void acceptsOfflineAndLocalAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--offline");

        assertTrue(new ModelRepositoryRunCapability()
                .snapshot(commandLine.getParseResult()).offline());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ModelRepositoryRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

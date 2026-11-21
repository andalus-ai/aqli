package tech.kayys.aqli.cli.commands.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies shared generation seed parsing and typed snapshots. */
class GenerationSeedRunCapabilityTest {

    @Test
    void snapshotsExplicitSeed() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--seed", "1234");

        GenerationSeedRunOptions options =
                new GenerationSeedRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(1234L, options.seed());
    }

    @Test
    void leavesSeedUnsetByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        GenerationSeedRunOptions options =
                new GenerationSeedRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.seed());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new GenerationSeedRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

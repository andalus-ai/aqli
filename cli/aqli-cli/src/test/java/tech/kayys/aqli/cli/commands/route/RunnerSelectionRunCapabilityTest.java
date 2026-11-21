package tech.kayys.aqli.cli.commands.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies explicit and optional runner route snapshots. */
class RunnerSelectionRunCapabilityTest {

    @Test
    void snapshotsExplicitRunnerRoute() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--runner", "safetensor");

        RunnerSelectionRunOptions options =
                new RunnerSelectionRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("safetensor", options.runner());
    }

    @Test
    void runnerMayBeSpecifiedWithoutAValueForAutomaticSelection() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--runner");

        RunnerSelectionRunOptions options =
                new RunnerSelectionRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("auto", options.runner());
    }

    @Test
    void leavesRunnerUnsetWhenOptionIsAbsent() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        RunnerSelectionRunOptions options =
                new RunnerSelectionRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.runner());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new RunnerSelectionRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

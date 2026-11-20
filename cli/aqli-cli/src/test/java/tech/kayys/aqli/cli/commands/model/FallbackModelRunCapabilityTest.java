package tech.kayys.aqli.cli.commands.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies fallback model list parsing and defaults. */
class FallbackModelRunCapabilityTest {

    @Test
    void snapshotsCommaSeparatedFallbackModels() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--fallback", "model-a,model-b");

        FallbackModelRunOptions options =
                new FallbackModelRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(List.of("model-a", "model-b"), options.modelIds());
    }

    @Test
    void snapshotsEmptyFallbackModelsByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        FallbackModelRunOptions options =
                new FallbackModelRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(List.of(), options.modelIds());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new FallbackModelRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

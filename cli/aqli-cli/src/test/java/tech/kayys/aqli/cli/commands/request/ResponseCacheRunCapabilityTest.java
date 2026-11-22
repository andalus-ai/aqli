package tech.kayys.aqli.cli.commands.request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies response-cache bypass parsing and defaults. */
class ResponseCacheRunCapabilityTest {

    @Test
    void snapshotsExplicitCacheBypass() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--no-cache");

        ResponseCacheRunOptions options =
                new ResponseCacheRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.bypassCache());
    }

    @Test
    void defaultsToUsingResponseCache() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ResponseCacheRunOptions options =
                new ResponseCacheRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.bypassCache());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ResponseCacheRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

package tech.kayys.aqli.cli.commands.response;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies response delivery flags and their defaults. */
class ResponseDeliveryRunCapabilityTest {

    @Test
    void snapshotsStreamingAndEncodingOptions() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--stream", "false", "--json", "--enable-json");

        ResponseDeliveryRunOptions options =
                new ResponseDeliveryRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.streaming());
        assertTrue(options.jsonOutput());
        assertTrue(options.openAiSse());
    }

    @Test
    void defaultsToStreamingWithoutStructuredEncoding() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ResponseDeliveryRunOptions options =
                new ResponseDeliveryRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.streaming());
        assertFalse(options.jsonOutput());
        assertFalse(options.openAiSse());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ResponseDeliveryRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

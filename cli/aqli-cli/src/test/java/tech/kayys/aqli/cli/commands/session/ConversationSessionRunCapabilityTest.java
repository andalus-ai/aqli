package tech.kayys.aqli.cli.commands.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies persistent conversation session option parsing and defaults. */
class ConversationSessionRunCapabilityTest {

    @Test
    void snapshotsSessionId() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--session-id", "conversation-123");

        ConversationSessionRunOptions options =
                new ConversationSessionRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("conversation-123", options.sessionId());
    }

    @Test
    void defaultsSessionIdToNull() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ConversationSessionRunOptions options =
                new ConversationSessionRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.sessionId());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ConversationSessionRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

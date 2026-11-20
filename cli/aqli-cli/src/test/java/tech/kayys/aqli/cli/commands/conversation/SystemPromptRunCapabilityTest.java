package tech.kayys.aqli.cli.commands.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies system-prompt parsing and nullable defaults. */
class SystemPromptRunCapabilityTest {

    @Test
    void snapshotsExplicitSystemPrompt() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--system", "You are a concise assistant.");

        SystemPromptRunOptions options =
                new SystemPromptRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("You are a concise assistant.", options.systemPrompt());
    }

    @Test
    void defaultsSystemPromptToNull() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        SystemPromptRunOptions options =
                new SystemPromptRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.systemPrompt());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new SystemPromptRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

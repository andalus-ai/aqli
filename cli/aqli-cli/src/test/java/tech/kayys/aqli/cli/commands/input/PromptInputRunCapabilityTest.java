package tech.kayys.aqli.cli.commands.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

class PromptInputRunCapabilityTest {

    @Test
    void snapshotsPromptUsingLongOption() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new PromptInputRunCapability().registerOptions(commandLine.getCommandSpec());

        commandLine.parseArgs("--prompt", "Summarize this document");

        PromptInputRunOptions options = new PromptInputRunCapability()
                .snapshot(commandLine.getParseResult());

        assertEquals("Summarize this document", options.prompt());
    }

    @Test
    void preservesShortAlias() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new PromptInputRunCapability().registerOptions(commandLine.getCommandSpec());

        commandLine.parseArgs("-p", "Hello");

        assertEquals("Hello", new PromptInputRunCapability()
                .snapshot(commandLine.getParseResult()).prompt());
    }

    @Test
    void snapshotsAbsentPromptAsNull() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new PromptInputRunCapability().registerOptions(commandLine.getCommandSpec());
        commandLine.parseArgs();

        assertNull(new PromptInputRunCapability()
                .snapshot(commandLine.getParseResult()).prompt());
    }

    @Command
    private static final class TestCommand {
    }
}

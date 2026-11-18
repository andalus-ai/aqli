package tech.kayys.aqli.cli.commands.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import tech.kayys.aqli.cli.spi.run.RunCapabilityRegistry;

/** Verifies the text capability's parsed-options snapshot contract. */
class TextRunCapabilityTest {

    @Test
    void snapshotsParsedTextOptionsThroughRegistry() {
        TestCommand command = new TestCommand();
        CommandLine commandLine = new CommandLine(command);
        new TextRunCapability().registerOptions(commandLine.getCommandSpec());
        commandLine.parseArgs(
                "--temperature", "0.7",
                "--top-p", "0.8",
                "--top-k", "12",
                "--repeat-penalty", "1.25",
                "--max-tokens", "96",
                "--mirostat", "2",
                "--grammar", "root ::= \"ok\"",
                "--json");

        RunCapabilityRegistry registry = RunCapabilityRegistry.load(
                TextRunCapabilityTest.class.getClassLoader());
        TextRunOptions options = registry.snapshot(
                "text", commandLine.getParseResult(), TextRunOptions.class).orElseThrow();

        assertEquals(0.7, options.temperature());
        assertEquals(0.8, options.topP());
        assertEquals(12, options.topK());
        assertEquals(1.25, options.repeatPenalty());
        assertEquals(96, options.maxTokens());
        assertEquals(2, options.mirostat());
        assertEquals("root ::= \"ok\"", options.grammar());
        assertEquals(true, options.jsonMode());
    }

    @Test
    void snapshotsCapabilityDefaultsWhenOptionsAreOmitted() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new TextRunCapability().registerOptions(commandLine.getCommandSpec());
        commandLine.parseArgs();

        TextRunOptions options = new TextRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(0.2, options.temperature());
        assertEquals(0.9, options.topP());
        assertEquals(40, options.topK());
        assertEquals(1.1, options.repeatPenalty());
        assertEquals(2048, options.maxTokens());
        assertEquals(0, options.mirostat());
        assertEquals(null, options.grammar());
        assertEquals(false, options.jsonMode());
    }

    @Command
    private static final class TestCommand {
        @picocli.CommandLine.Option(names = "--json")
        private boolean json;
    }
}

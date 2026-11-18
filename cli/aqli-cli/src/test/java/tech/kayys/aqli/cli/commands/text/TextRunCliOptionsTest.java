package tech.kayys.aqli.cli.commands.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * Verifies that moving text flags behind the mixin preserves their CLI contract.
 */
class TextRunCliOptionsTest {

    @Test
    void preservesTextOptionNamesDefaultsAndSnapshot() {
        TestCommand command = new TestCommand();

        new CommandLine(command).parseArgs();

        TextRunOptions options = command.text.snapshot(false);
        assertEquals(0.2, options.temperature());
        assertEquals(0.9, options.topP());
        assertEquals(40, options.topK());
        assertEquals(1.1, options.repeatPenalty());
        assertFalse(options.jsonMode());
        assertEquals(2048, options.maxTokens());
        assertEquals(0, options.mirostat());
        assertNull(options.grammar());
    }

    @Test
    void parsesTextOptionOverrides() {
        TestCommand command = new TestCommand();

        new CommandLine(command).parseArgs(
                "--temperature", "0.7",
                "--top-p", "0.8",
                "--top-k", "12",
                "--repeat-penalty", "1.25",
                "--max-tokens", "96",
                "--mirostat", "2",
                "--grammar", "root ::= \"ok\"");

        TextRunOptions options = command.text.snapshot(true);
        assertEquals(0.7, options.temperature());
        assertEquals(0.8, options.topP());
        assertEquals(12, options.topK());
        assertEquals(1.25, options.repeatPenalty());
        assertTrue(options.jsonMode());
        assertEquals(96, options.maxTokens());
        assertEquals(2, options.mirostat());
        assertEquals("root ::= \"ok\"", options.grammar());
    }

    @Command
    private static final class TestCommand {
        @Mixin
        private TextRunCliOptions text = new TextRunCliOptions();
    }
}

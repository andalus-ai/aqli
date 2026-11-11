package tech.kayys.aqli.cli.commands.replay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplayShellCommandFormatterTest {
    @Test
    void leavesSimpleArgumentsUnquoted() {
        assertEquals("aqli run --model hf:org/model", ReplayShellCommandFormatter.format(
                List.of("aqli", "run", "--model", "hf:org/model")));
    }

    @Test
    void quotesWhitespaceAndEscapesEmbeddedSingleQuotes() {
        assertEquals("aqli run --prompt 'hello world' --name 'it'\"'\"'s fine'",
                ReplayShellCommandFormatter.format(
                        List.of("aqli", "run", "--prompt", "hello world", "--name", "it's fine")));
    }

    @Test
    void representsNullAndEmptyArgumentsAsEmptyShellWords() {
        assertEquals("'' '' x", ReplayShellCommandFormatter.format(
                java.util.Arrays.asList(null, "", "x")));
    }

    @Test
    void formatsEmptyArgumentListAsEmptyString() {
        assertEquals("", ReplayShellCommandFormatter.format(List.of()));
    }
}

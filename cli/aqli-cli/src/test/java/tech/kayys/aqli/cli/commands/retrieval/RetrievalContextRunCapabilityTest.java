package tech.kayys.aqli.cli.commands.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies retrieval context option aliases, splitting, and defaults. */
class RetrievalContextRunCapabilityTest {

    @Test
    void snapshotsRetrievalContextAndEmbeddingOptions() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--rag-context", "first|second",
                "--rag-file", "one.txt,two.txt",
                "--rag-max-chars", "6400",
                "--embed-model", "hf:example/embedder");

        RetrievalContextRunOptions options =
                new RetrievalContextRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(java.util.List.of("first", "second"), options.contexts());
        assertEquals(java.util.List.of("one.txt", "two.txt"), options.files());
        assertEquals(6400, options.maxChars());
        assertEquals("hf:example/embedder", options.embeddingModel());
    }

    @Test
    void snapshotsContextFileAliasAndDefaults() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--context-file", "context.txt");

        RetrievalContextRunOptions options =
                new RetrievalContextRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(java.util.List.of(), options.contexts());
        assertEquals(java.util.List.of("context.txt"), options.files());
        assertEquals(12000, options.maxChars());
        assertNull(options.embeddingModel());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new RetrievalContextRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

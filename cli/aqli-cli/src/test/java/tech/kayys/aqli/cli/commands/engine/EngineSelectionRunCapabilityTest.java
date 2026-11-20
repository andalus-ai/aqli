package tech.kayys.aqli.cli.commands.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies engine-selection aliases and defaults. */
class EngineSelectionRunCapabilityTest {

    @Test
    void snapshotsEngineSelectionOptionsAndAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--gguf-engine", "java", "--backend", "metal",
                "--java", "--llama-cpp", "--bench");

        EngineSelectionRunOptions options =
                new EngineSelectionRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("java", options.engine());
        assertEquals("metal", options.backend());
        assertTrue(options.javaNative());
        assertTrue(options.llamaCpp());
        assertTrue(options.benchmark());
    }

    @Test
    void defaultsEngineSelectionOptions() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        EngineSelectionRunOptions options =
                new EngineSelectionRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(null, options.engine());
        assertEquals(null, options.backend());
        assertFalse(options.javaNative());
        assertFalse(options.llamaCpp());
        assertFalse(options.benchmark());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new EngineSelectionRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

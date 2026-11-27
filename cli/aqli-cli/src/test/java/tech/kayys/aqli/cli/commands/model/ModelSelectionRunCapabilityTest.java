package tech.kayys.aqli.cli.commands.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

class ModelSelectionRunCapabilityTest {

    @Test
    void snapshotsModelUsingLongOption() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ModelSelectionRunCapability().registerOptions(commandLine.getCommandSpec());

        commandLine.parseArgs("--model", "hf:unsloth/gemma-4-12b-it-GGUF");

        assertEquals("hf:unsloth/gemma-4-12b-it-GGUF",
                new ModelSelectionRunCapability().snapshot(commandLine.getParseResult()).modelId());
    }

    @Test
    void preservesShortAlias() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ModelSelectionRunCapability().registerOptions(commandLine.getCommandSpec());

        commandLine.parseArgs("-m", "local-model");

        assertEquals("local-model",
                new ModelSelectionRunCapability().snapshot(commandLine.getParseResult()).modelId());
    }

    @Test
    void snapshotsAbsentModelAsNull() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ModelSelectionRunCapability().registerOptions(commandLine.getCommandSpec());
        commandLine.parseArgs();

        assertNull(new ModelSelectionRunCapability().snapshot(commandLine.getParseResult()).modelId());
    }

    @Command
    private static final class TestCommand {
    }
}

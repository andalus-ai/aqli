package tech.kayys.aqli.cli.commands.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies local model-source option parsing and snapshots. */
class ModelSourceRunCapabilityTest {

    @Test
    void snapshotsLocalFileDirectoryAndCustomPath() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--modelFile", "weights.gguf",
                "--modelDir", "checkpoint/",
                "--model-path", "custom/model.onnx");

        ModelSourceRunOptions options =
                new ModelSourceRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("weights.gguf", options.modelFile());
        assertEquals("checkpoint/", options.modelDirectory());
        assertEquals("custom/model.onnx", options.modelPath());
    }

    @Test
    void leavesAllLocalSourcesUnsetByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ModelSourceRunOptions options =
                new ModelSourceRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.modelFile());
        assertNull(options.modelDirectory());
        assertNull(options.modelPath());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ModelSourceRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

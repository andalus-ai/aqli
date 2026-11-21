package tech.kayys.aqli.cli.commands.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies feature-pipeline aliases and typed snapshots. */
class FeaturePipelineRunCapabilityTest {

    @Test
    void snapshotsPipelineOption() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--pipeline", "vision-encoder");

        FeaturePipelineRunOptions options =
                new FeaturePipelineRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("vision-encoder", options.pipelineId());
    }

    @Test
    void snapshotsLegacyFeatureAlias() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--feature", "multimodal");

        FeaturePipelineRunOptions options =
                new FeaturePipelineRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("multimodal", options.pipelineId());
    }

    @Test
    void leavesPipelineUnsetByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        FeaturePipelineRunOptions options =
                new FeaturePipelineRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.pipelineId());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new FeaturePipelineRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

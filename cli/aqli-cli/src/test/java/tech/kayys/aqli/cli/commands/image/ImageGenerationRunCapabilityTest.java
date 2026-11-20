package tech.kayys.aqli.cli.commands.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import tech.kayys.aqli.cli.spi.run.RunCapabilityRegistry;

/** Verifies image-generation option parsing and typed snapshots. */
class ImageGenerationRunCapabilityTest {

    @Test
    void snapshotsImageGenerationOverridesThroughRegistry() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--steps", "30",
                "--cfg", "6.5",
                "--width", "768",
                "--height", "512");

        ImageGenerationRunOptions options = RunCapabilityRegistry.load(
                ImageGenerationRunCapabilityTest.class.getClassLoader())
                .snapshot("image-generation", commandLine.getParseResult(), ImageGenerationRunOptions.class)
                .orElseThrow();

        assertEquals(30, options.steps());
        assertEquals(6.5f, options.guidanceScale());
        assertEquals(768, options.width());
        assertEquals(512, options.height());
    }

    @Test
    void leavesOptionalImageGenerationSettingsUnsetByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ImageGenerationRunOptions options = new ImageGenerationRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.steps());
        assertNull(options.guidanceScale());
        assertNull(options.width());
        assertNull(options.height());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ImageGenerationRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

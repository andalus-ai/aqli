package tech.kayys.aqli.cli.commands.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies image metadata option snapshots. */
class ImageMetadataRunCapabilityTest {

    @Test
    void snapshotsImageMetadataOperations() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--image-metadata-output", "image.json",
                "--image-metadata-sidecar",
                "--image-metadata-verify", "verify.json",
                "--image-metadata-refresh", "refresh.json");

        ImageMetadataRunOptions options =
                new ImageMetadataRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("image.json", options.imageOutputPath());
        assertTrue(options.imageSidecar());
        assertEquals("verify.json", options.imageVerifyPath());
        assertEquals("refresh.json", options.imageRefreshPath());
    }

    @Test
    void defaultsImageMetadataOperationsToDisabled() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ImageMetadataRunOptions options =
                new ImageMetadataRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.imageOutputPath());
        assertFalse(options.imageSidecar());
        assertNull(options.imageVerifyPath());
        assertNull(options.imageRefreshPath());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ImageMetadataRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

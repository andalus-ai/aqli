package tech.kayys.aqli.cli.commands.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies metadata option aliases and immutable snapshots. */
class MediaMetadataRunCapabilityTest {

    @Test
    void snapshotsAudioAndImageMetadataOperations() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--tts-metadata-output", "audio.json",
                "--tts-metadata-sidecar",
                "--tts-replay", "audio-replay.json",
                "--tts-verify", "audio-verify.json",
                "--tts-refresh-metadata", "audio-refresh.json",
                "--image-metadata-output", "image.json",
                "--image-metadata-sidecar",
                "--image-metadata-verify", "image-verify.json",
                "--image-metadata-refresh", "image-refresh.json");

        MediaMetadataRunOptions options =
                new MediaMetadataRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("audio.json", options.audioOutputPath());
        assertTrue(options.audioSidecar());
        assertEquals("audio-replay.json", options.audioReplayPath());
        assertEquals("audio-verify.json", options.audioVerifyPath());
        assertEquals("audio-refresh.json", options.audioRefreshPath());
        assertEquals("image.json", options.imageOutputPath());
        assertTrue(options.imageSidecar());
        assertEquals("image-verify.json", options.imageVerifyPath());
        assertEquals("image-refresh.json", options.imageRefreshPath());
    }

    @Test
    void defaultsMetadataOperationsToDisabled() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        MediaMetadataRunOptions options =
                new MediaMetadataRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.audioOutputPath());
        assertFalse(options.audioSidecar());
        assertNull(options.audioReplayPath());
        assertNull(options.audioVerifyPath());
        assertNull(options.audioRefreshPath());
        assertNull(options.imageOutputPath());
        assertFalse(options.imageSidecar());
        assertNull(options.imageVerifyPath());
        assertNull(options.imageRefreshPath());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new MediaMetadataRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

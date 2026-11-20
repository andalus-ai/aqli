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
                "--tts-verify", "audio-verify.json",
                "--tts-refresh-metadata", "audio-refresh.json");

        MediaMetadataRunOptions options =
                new MediaMetadataRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("audio.json", options.audioOutputPath());
        assertTrue(options.audioSidecar());
        assertEquals("audio-verify.json", options.audioVerifyPath());
        assertEquals("audio-refresh.json", options.audioRefreshPath());
    }

    @Test
    void defaultsMetadataOperationsToDisabled() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        MediaMetadataRunOptions options =
                new MediaMetadataRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.audioOutputPath());
        assertFalse(options.audioSidecar());
        assertNull(options.audioVerifyPath());
        assertNull(options.audioRefreshPath());
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

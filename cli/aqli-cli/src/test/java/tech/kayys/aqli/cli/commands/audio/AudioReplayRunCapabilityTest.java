package tech.kayys.aqli.cli.commands.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies audio replay option aliases and immutable snapshots. */
class AudioReplayRunCapabilityTest {

    @Test
    void snapshotsCanonicalReplayOption() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--audio-metadata-replay", "audio-replay.json");

        AudioReplayRunOptions options =
                new AudioReplayRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("audio-replay.json", options.replayPath());
    }

    @Test
    void snapshotsLegacyReplayAlias() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--tts-replay", "legacy-replay.json");

        AudioReplayRunOptions options =
                new AudioReplayRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("legacy-replay.json", options.replayPath());
    }

    @Test
    void defaultsReplayPathToAbsent() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        AudioReplayRunOptions options =
                new AudioReplayRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.replayPath());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new AudioReplayRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}

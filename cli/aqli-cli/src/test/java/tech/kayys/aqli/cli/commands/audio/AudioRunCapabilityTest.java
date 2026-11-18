package tech.kayys.aqli.cli.commands.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies audio option registration and immutable snapshots. */
class AudioRunCapabilityTest {

    @Test
    void registersAudioOptionsAndSnapshotsExplicitValues() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new AudioRunCapability().registerOptions(commandLine.getCommandSpec());
        commandLine.parseArgs(
                "--tts-voice", "en_female_1",
                "--voice-language", "en",
                "--voice-gender", "female",
                "--tts-voice-search", "english female",
                "--codec", "models/codec",
                "--no-tts-auto-pull-codec",
                "--tts-max-frames", "512",
                "--max-seconds", "12.5",
                "--tts-audio-format", "flac",
                "--audio-quality", "lossless",
                "--audio-bitrate-kbps", "192",
                "--tts-audio-channels", "stereo",
                "--codec-decode", "streaming",
                "--tts-random-seed",
                "--no-tts-audio-polish",
                "--no-audio-normalize",
                "--tts-gain-db", "1.5",
                "--audio-peak-dbfs", "-2.5",
                "--tts-fade-ms", "8",
                "--tts-trim-silence",
                "--audio-trim-dbfs", "-50",
                "--tts-trim-padding-ms", "20",
                "--tts-stream-chunk-kb", "96",
                "--tts-stream-progress-frames", "16",
                "--no-tts-stream-progress",
                "--audio-stream-pcm",
                "--tts-live-audio",
                "--no-tts-low-latency-audio",
                "--flac-compression", "6");

        AudioRunOptions options = new AudioRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("en_female_1", options.voice());
        assertEquals("en", options.voiceLanguage());
        assertEquals("female", options.voiceGender());
        assertEquals("english female", options.voiceSearch());
        assertEquals("models/codec", options.codec());
        assertTrue(options.disableAutoCodec());
        assertEquals(512, options.maxFrames());
        assertEquals(12.5, options.maxSeconds());
        assertEquals("flac", options.format());
        assertEquals("lossless", options.quality());
        assertEquals(192, options.bitrateKbps());
        assertEquals("stereo", options.channels());
        assertEquals("streaming", options.codecDecode());
        assertTrue(options.randomSeed());
        assertTrue(options.disablePolish());
        assertTrue(options.disableNormalize());
        assertEquals(1.5, options.gainDb());
        assertEquals(-2.5, options.peakDb());
        assertEquals(8.0, options.fadeMs());
        assertTrue(options.trimSilence());
        assertEquals(-50.0, options.trimDbfs());
        assertEquals(20.0, options.trimPaddingMs());
        assertEquals(96, options.streamChunkKb());
        assertEquals(16, options.streamProgressFrames());
        assertTrue(options.disableStreamProgress());
        assertTrue(options.streamPcm());
        assertTrue(options.liveAudio());
        assertTrue(options.disableLowLatency());
        assertEquals(6, options.flacCompression());
    }

    @Test
    void snapshotsUnsetAudioOptionsAsNullOrFalse() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new AudioRunCapability().registerOptions(commandLine.getCommandSpec());
        commandLine.parseArgs();

        AudioRunOptions options = new AudioRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.voice());
        assertNull(options.codec());
        assertNull(options.voiceSearch());
        assertFalse(options.disableAutoCodec());
        assertNull(options.maxFrames());
        assertNull(options.maxSeconds());
        assertNull(options.format());
        assertFalse(options.randomSeed());
        assertFalse(options.disablePolish());
        assertFalse(options.liveAudio());
        assertNull(options.flacCompression());
    }


    @Test
    void replayMetadataOnlyFillsOptionsNotExplicitlyProvidedOnTheCommandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new AudioRunCapability().registerOptions(commandLine.getCommandSpec());
        commandLine.parseArgs("--audio-format", "wav", "--audio-gain-db", "2");

        AudioRunOptions cliOptions = new AudioRunCapability().snapshot(commandLine.getParseResult());
        AudioMetadataReplayParser.Plan replayPlan = new AudioMetadataReplayParser.Plan(List.of(
                new AudioMetadataReplayParser.ReplayOption(AudioMetadataReplayParser.Option.VOICE, "ReplayVoice"),
                new AudioMetadataReplayParser.ReplayOption(AudioMetadataReplayParser.Option.AUDIO_FORMAT, "flac"),
                new AudioMetadataReplayParser.ReplayOption(AudioMetadataReplayParser.Option.AUDIO_QUALITY, "lossless"),
                new AudioMetadataReplayParser.ReplayOption(AudioMetadataReplayParser.Option.GAIN_DB, 5.0),
                new AudioMetadataReplayParser.ReplayOption(
                        AudioMetadataReplayParser.Option.DISABLE_AUDIO_POLISH, Boolean.TRUE),
                new AudioMetadataReplayParser.ReplayOption(AudioMetadataReplayParser.Option.TRIM_SILENCE, Boolean.TRUE)),
                "replay.wav");

        AudioRunOptions effective = cliOptions.withReplayPlan(replayPlan);

        assertEquals("ReplayVoice", effective.voice());
        assertEquals("wav", effective.format());
        assertEquals("lossless", effective.quality());
        assertEquals(2.0, effective.gainDb());
        assertTrue(effective.disablePolish());
        assertTrue(effective.trimSilence());
        assertNull(cliOptions.voice());
        assertNull(cliOptions.quality());
        assertFalse(cliOptions.disablePolish());
    }

    @Command
    private static final class TestCommand {
    }
}

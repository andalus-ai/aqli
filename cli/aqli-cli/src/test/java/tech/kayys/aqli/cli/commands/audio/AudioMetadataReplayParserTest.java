package tech.kayys.aqli.cli.commands.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AudioMetadataReplayParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesTypedAliasesPayloadFallbackAndLastOutput() throws Exception {
        Path sidecar = tempDir.resolve("replay.json");
        Files.writeString(sidecar, """
                {
                  "model": "hf:org/model",
                  "provider": "payload-provider",
                  "output_path": "payload.wav",
                  "replay_args": [
                    "--tts-voice", "en-US",
                    "--tts-audio-format", "flac",
                    "--tts-bitrate-kbps", "192",
                    "--max-frames", "64",
                    "--tts-duration", "12.5",
                    "--tts-gain-db", "1.25",
                    "--audio-trim-silence",
                    "--output", "first.wav",
                    "--output", "last.wav",
                    "--unknown-option", "ignored"
                  ],
                  "metadata": {"tts_seed": "42", "audio_bitrate_kbps": "256"}
                }
                """);

        AudioMetadataReplayParser.Plan plan = new AudioMetadataReplayParser().parse(sidecar);
        assertEquals("last.wav", plan.outputPath());
        assertEquals("hf:org/model", first(plan, AudioMetadataReplayParser.Option.MODEL).value());
        assertEquals("payload-provider", first(plan, AudioMetadataReplayParser.Option.PROVIDER).value());
        assertEquals("en-US", first(plan, AudioMetadataReplayParser.Option.VOICE).value());
        assertEquals(64, first(plan, AudioMetadataReplayParser.Option.MAX_FRAMES).value());
        assertEquals(12.5d, first(plan, AudioMetadataReplayParser.Option.MAX_SECONDS).value());
        assertEquals(42L, first(plan, AudioMetadataReplayParser.Option.SEED).value());
        assertEquals(Boolean.TRUE, first(plan, AudioMetadataReplayParser.Option.TRIM_SILENCE).value());
    }

    @Test
    void returnsNullForNonObjectRoot() throws Exception {
        Path sidecar = tempDir.resolve("invalid.json");
        Files.writeString(sidecar, "[]");
        assertNull(new AudioMetadataReplayParser().parse(sidecar));
    }

    @Test
    void rejectsMalformedTypedArguments() throws Exception {
        Path sidecar = tempDir.resolve("invalid-number.json");
        Files.writeString(sidecar, "{\"replay_args\":[\"--audio-bitrate\",\"not-a-number\"]}");
        assertThrows(IllegalArgumentException.class, () -> new AudioMetadataReplayParser().parse(sidecar));
    }

    private static AudioMetadataReplayParser.ReplayOption first(
            AudioMetadataReplayParser.Plan plan, AudioMetadataReplayParser.Option option) {
        return plan.options().stream().filter(item -> item.option() == option).findFirst().orElseThrow();
    }
}

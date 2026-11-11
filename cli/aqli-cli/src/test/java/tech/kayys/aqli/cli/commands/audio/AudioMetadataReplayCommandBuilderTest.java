package tech.kayys.aqli.cli.commands.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioMetadataReplayCommandBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsReplayArgumentsInStableOrderAndOmitsUnsetValues() {
        var request = new AudioMetadataReplayCommandBuilder.Request(
                "hf:org/model", "native", "onnx", "say hello", "en-US",
                "wav", "1234", "result.wav", null, 192, "stereo",
                null, 48, 12.5, 1.5, -1.0, 80.0, -42.0, 120.0,
                5, true, false, true);

        assertEquals(List.of(
                "aqli", "run",
                "--model", "hf:org/model",
                "--provider", "native",
                "--runner", "onnx",
                "--prompt", "say hello",
                "--voice", "en-US",
                "--audio-format", "wav",
                "--seed", "1234",
                "--output", "result.wav",
                "--audio-bitrate", "192",
                "--audio-channels", "stereo",
                "--tts-max-frames", "48",
                "--tts-max-seconds", "12.5",
                "--audio-gain-db", "1.5",
                "--audio-peak-db", "-1.0",
                "--audio-fade-ms", "80.0",
                "--audio-trim-db", "-42.0",
                "--audio-trim-padding-ms", "120.0",
                "--flac-compression", "5",
                "--no-audio-polish",
                "--audio-trim-silence"),
                AudioMetadataReplayCommandBuilder.build(request));
    }

    @Test
    void resolvesLocalModelDirectoriesAndFilesToSpecificOptions() throws Exception {
        Path modelDirectory = Files.createDirectory(tempDir.resolve("model"));
        Path modelFile = Files.createFile(tempDir.resolve("model.gguf"));

        assertModelTarget(modelDirectory.toString(), "--modelDir");
        assertModelTarget(modelFile.toString(), "--modelFile");
    }

    private static void assertModelTarget(String model, String expectedFlag) {
        var request = new AudioMetadataReplayCommandBuilder.Request(
                model, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, false, false, false);
        List<String> args = AudioMetadataReplayCommandBuilder.build(request);
        assertEquals(expectedFlag, args.get(2));
        assertEquals(model, args.get(3));
    }
}

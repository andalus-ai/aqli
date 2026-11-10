package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AudioMetadataSidecarStoreTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesAudioMetadataWithOutputIntegrityAndReplayInformation() throws Exception {
        Path audio = tempDir.resolve("generated.wav");
        Path sidecar = tempDir.resolve("metadata/generated.json");
        Files.writeString(audio, "audio-bytes");
        Map<String, Object> metadata = Map.of("audio_sample_rate", 24000, "audio_duration_seconds", 1.25);
        List<String> replayArgs = List.of("run", "--model", "demo");

        AudioMetadataSidecarStore.WriteResult result = AudioMetadataSidecarStore.write(
                new AudioMetadataSidecarStore.WriteRequest(
                        audio, sidecar, "demo", "java", "standalone", "hello", "voice-1",
                        "wav", replayArgs, "aqli run --model demo", metadata));

        assertFalse(result.skippedBecausePathsMatch());
        assertEquals(sidecar.toAbsolutePath().normalize(), result.sidecarPath());
        assertTrue(Files.isRegularFile(sidecar));

        JsonNode payload = mapper.readTree(sidecar.toFile());
        assertEquals("aqli_audio_metadata", payload.path("type").asText());
        assertEquals(2, payload.path("schema_version").asInt());
        assertEquals(audio.toAbsolutePath().normalize().toString(), payload.path("output_path").asText());
        assertEquals(Files.size(audio), payload.path("output_size_bytes").asLong());
        assertEquals(MediaFileIntegrity.sha256Hex(audio), payload.path("output_sha256").asText());
        assertEquals("demo", payload.path("model").asText());
        assertEquals("java", payload.path("provider").asText());
        assertEquals("standalone", payload.path("runner").asText());
        assertEquals("voice-1", payload.path("voice_selector").asText());
        assertEquals("wav", payload.path("audio_format").asText());
        assertEquals("aqli run --model demo", payload.path("replay_command").asText());
        assertEquals("run", payload.path("replay_args").get(0).asText());
        assertEquals(24000, payload.path("metadata").path("audio_sample_rate").asInt());
    }

    @Test
    void skipsSidecarWhenItWouldOverwriteAudioOutput() throws Exception {
        Path audio = tempDir.resolve("generated.wav");
        Files.writeString(audio, "audio-bytes");

        AudioMetadataSidecarStore.Result result = AudioMetadataSidecarStore.write(
                new AudioMetadataSidecarStore.Request(
                        audio, audio, "demo", "java", null, "hello", null,
                        "wav", List.of(), "", Map.of()));

        assertTrue(result.skippedBecausePathsMatch());
        assertEquals(audio.toAbsolutePath().normalize(), result.sidecarPath());
        assertEquals("audio-bytes", Files.readString(audio));
    }

    @Test
    void createsParentDirectoriesForSidecar() throws Exception {
        Path audio = tempDir.resolve("generated.wav");
        Path sidecar = tempDir.resolve("nested/deeper/metadata.json");
        Files.writeString(audio, "audio-bytes");

        AudioMetadataSidecarStore.write(new AudioMetadataSidecarStore.Request(
                audio, sidecar, null, null, null, null, null, "wav", List.of(), "", Map.of()));

        assertTrue(Files.isRegularFile(sidecar));
    }
}

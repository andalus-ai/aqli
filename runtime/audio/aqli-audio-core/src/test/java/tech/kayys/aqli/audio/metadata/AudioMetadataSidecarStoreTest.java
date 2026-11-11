package tech.kayys.aqli.audio.metadata;

import tech.kayys.aqli.media.integrity.MediaFileIntegrity;

import tech.kayys.aqli.cli.commands.MediaFileIntegrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        AudioMetadataSidecarStore.WriteResult result = AudioMetadataSidecarStore.write(
                new AudioMetadataSidecarStore.WriteRequest(
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

        AudioMetadataSidecarStore.write(new AudioMetadataSidecarStore.WriteRequest(
                audio, sidecar, null, null, null, null, null, "wav", List.of(), "", Map.of()));

        assertTrue(Files.isRegularFile(sidecar));
    }

    @Test
    void verifiesOutputSizeAndDigestAndSupportsExplicitOutputOverride() throws Exception {
        Path audio = tempDir.resolve("actual.wav");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(audio, "verified-audio");
        Files.writeString(sidecar, """
                {
                  "type": "aqli_audio_metadata",
                  "output_path": "missing.wav",
                  "output_size_bytes": 14,
                  "output_sha256": "%s"
                }
                """.formatted(MediaFileIntegrity.sha256Hex(audio)));

        AudioMetadataSidecarStore.VerificationResult result =
                AudioMetadataSidecarStore.verify(sidecar, audio);

        assertEquals(audio.toAbsolutePath().normalize(), result.audioPath());
        assertEquals(Files.size(audio), result.sizeBytes());
        assertEquals(MediaFileIntegrity.sha256Hex(audio), result.sha256());
    }

    @Test
    void rejectsVerificationWhenOutputDigestDoesNotMatch() throws Exception {
        Path audio = tempDir.resolve("generated.wav");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(audio, "audio-bytes");
        Files.writeString(sidecar, """
                {"output_path":"generated.wav","output_sha256":"0000"}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> AudioMetadataSidecarStore.verify(sidecar, null));
    }

    @Test
    void refreshesExistingSidecarIntegrityFields() throws Exception {
        Path audio = tempDir.resolve("generated.wav");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(audio, "updated-audio");
        Files.writeString(sidecar, """
                {"type":"aqli_audio_metadata","schema_version":1,"output_path":"generated.wav"}
                """);

        AudioMetadataSidecarStore.RefreshResult result =
                AudioMetadataSidecarStore.refresh(sidecar, null);

        JsonNode payload = mapper.readTree(sidecar.toFile());
        assertEquals(sidecar.toAbsolutePath().normalize(), result.sidecarPath());
        assertEquals(audio.toAbsolutePath().normalize(), result.audioPath());
        assertEquals(2, payload.path("schema_version").asInt());
        assertTrue(payload.hasNonNull("refreshed_at"));
        assertEquals(Files.size(audio), payload.path("output_size_bytes").asLong());
        assertEquals(MediaFileIntegrity.sha256Hex(audio), payload.path("output_sha256").asText());
    }
}

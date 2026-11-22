package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioMetadataSidecarCliHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void writesVerifiesAndRefreshesSidecarUsingOutputOverride() throws Exception {
        Path audio = tempDir.resolve("generated.wav");
        Path override = tempDir.resolve("replacement.wav");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(audio, "audio-payload");
        Files.writeString(override, "audio-payload");

        AudioMetadataSidecarCliHandler.write(new MediaMetadataSidecarCliHandler.AudioSidecarRequest(
                audio, sidecar.toString(), true, "model", "provider", "runner",
                "prompt", "voice", "wav", List.of("aqli", "run"), Map.of("sample_rate", 24000)));

        assertTrue(Files.isRegularFile(sidecar));
        assertTrue(AudioMetadataSidecarCliHandler.verify(sidecar.toString(), override.toString()));
        assertTrue(AudioMetadataSidecarCliHandler.refresh(sidecar.toString(), override.toString()));
        assertTrue(AudioMetadataSidecarCliHandler.verify(sidecar.toString(), override.toString()));
    }

    @Test
    void doesNotOverwriteAudioWhenSidecarPathMatchesOutput() throws Exception {
        Path audio = tempDir.resolve("generated.wav");
        Files.writeString(audio, "audio-payload");

        AudioMetadataSidecarCliHandler.write(new MediaMetadataSidecarCliHandler.AudioSidecarRequest(
                audio, audio.toString(), true, "model", "provider", "runner",
                "prompt", null, "wav", List.of(), Map.of()));

        assertEquals("audio-payload", Files.readString(audio));
    }

    @Test
    void skipsWritingWhenNeitherFlagNorExplicitPathIsProvided() throws Exception {
        Path audio = tempDir.resolve("generated.wav");
        Files.writeString(audio, "audio-payload");
        Path implicitSidecar = tempDir.resolve("generated.json");

        AudioMetadataSidecarCliHandler.write(new MediaMetadataSidecarCliHandler.AudioSidecarRequest(
                audio, null, false, "model", "provider", "runner",
                "prompt", null, "wav", List.of(), Map.of()));

        assertFalse(Files.exists(implicitSidecar));
    }
    @Test
    void reportsAudioStoreFailuresWithAudioContext() throws Exception {
        Path missingSidecar = tempDir.resolve("missing-audio.json");
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        PrintStream originalError = System.err;
        try (PrintStream captured = new PrintStream(error, true, StandardCharsets.UTF_8)) {
            System.setErr(captured);
            assertFalse(AudioMetadataSidecarCliHandler.verify(missingSidecar.toString(), null));
        } finally {
            System.setErr(originalError);
        }
        assertTrue(error.toString(StandardCharsets.UTF_8)
                .contains("Error: failed to verify audio metadata: audio metadata file not found:"));
    }

    @Test
    void returnsFalseWhenVerificationOrRefreshSidecarDoesNotExist() {
        Path missingSidecar = tempDir.resolve("missing-audio.json");

        assertFalse(AudioMetadataSidecarCliHandler.verify(missingSidecar.toString(), null));
        assertFalse(AudioMetadataSidecarCliHandler.refresh(missingSidecar.toString(), null));
    }
}

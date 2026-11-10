package tech.kayys.aqli.cli.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persists the audio metadata sidecar associated with a generated audio file.
 *
 * <p>The command remains responsible for interpreting CLI options and building replay arguments;
 * this class owns sidecar path safety, file integrity metadata, and JSON persistence.</p>
 */
final class AudioMetadataSidecarWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AudioMetadataSidecarWriter() {
    }

    static Result write(Request request) throws Exception {
        Path audioPath = request.audioPath().toAbsolutePath().normalize();
        Path sidecarPath = request.sidecarPath().toAbsolutePath().normalize();
        if (audioPath.equals(sidecarPath)) {
            return new Result(sidecarPath, true);
        }

        Path parent = sidecarPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "aqli_audio_metadata");
        payload.put("schema_version", 2);
        payload.put("created_at", java.time.Instant.now().toString());
        payload.put("output_path", audioPath.toString());
        payload.put("output_size_bytes", Files.size(audioPath));
        payload.put("output_sha256", MediaFileIntegrity.sha256Hex(audioPath));
        payload.put("model", request.model());
        payload.put("provider", request.provider());
        if (request.runner() != null && !request.runner().isBlank()) {
            payload.put("runner", request.runner());
        }
        payload.put("prompt", request.prompt());
        if (request.voiceSelector() != null && !request.voiceSelector().isBlank()) {
            payload.put("voice_selector", request.voiceSelector());
        }
        payload.put("audio_format", request.audioFormat());
        payload.put("replay_args", request.replayArgs());
        payload.put("replay_command", request.replayCommand());
        payload.put("metadata", request.metadata() == null ? Map.of() : new LinkedHashMap<>(request.metadata()));

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        Files.writeString(sidecarPath, json);
        return new Result(sidecarPath, false);
    }

    record Request(
            Path audioPath,
            Path sidecarPath,
            String model,
            String provider,
            String runner,
            String prompt,
            String voiceSelector,
            String audioFormat,
            java.util.List<String> replayArgs,
            String replayCommand,
            Map<String, Object> metadata) {
    }

    record Result(Path sidecarPath, boolean skippedBecausePathsMatch) {
    }
}

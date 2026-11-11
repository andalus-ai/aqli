package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Persists image metadata sidecars and the integrity information for their output artifact. */
final class ImageMetadataSidecarStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ImageMetadataSidecarStore() {
    }

    static WriteResult write(WriteRequest request) throws Exception {
        Path imagePath = request.imagePath().toAbsolutePath().normalize();
        Path sidecarPath = request.sidecarPath().toAbsolutePath().normalize();
        if (imagePath.equals(sidecarPath)) {
            return new WriteResult(sidecarPath, true);
        }

        Path parent = sidecarPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "aqli_image_metadata");
        payload.put("schema_version", 1);
        payload.put("created_at", Instant.now().toString());
        payload.put("output_path", imagePath.toString());
        payload.put("output_size_bytes", Files.size(imagePath));
        payload.put("output_sha256", MediaFileIntegrity.sha256Hex(imagePath));
        payload.put("model", request.model());
        payload.put("provider", request.provider());
        if (request.runner() != null && !request.runner().isBlank()) {
            payload.put("runner", request.runner());
        }
        payload.put("prompt", request.prompt());
        payload.put("image_format", request.imageFormat());
        addParameter(payload, "seed", request.seed());
        addParameter(payload, "steps", request.steps());
        addParameter(payload, "guidance_scale", request.guidanceScale());
        addParameter(payload, "width", request.width());
        addParameter(payload, "height", request.height());
        payload.put("replay_args", request.replayArgs());
        payload.put("replay_command", request.replayCommand());
        payload.put("metadata", request.metadata() == null ? Map.of() : new LinkedHashMap<>(request.metadata()));

        Files.writeString(sidecarPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        return new WriteResult(sidecarPath, false);
    }

    private static void addParameter(Map<String, Object> payload, String key, Object value) {
        if (value == null) {
            return;
        }
        if (!String.valueOf(value).isBlank()) {
            payload.put(key, value);
        }
    }

    record WriteRequest(
            Path imagePath,
            Path sidecarPath,
            String model,
            String provider,
            String runner,
            String prompt,
            String imageFormat,
            String seed,
            Object steps,
            Object guidanceScale,
            Object width,
            Object height,
            List<String> replayArgs,
            String replayCommand,
            Map<String, Object> metadata) {
    }

    record WriteResult(Path sidecarPath, boolean skippedBecausePathsMatch) {
    }
}

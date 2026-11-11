package tech.kayys.aqli.audio.metadata;

import tech.kayys.aqli.media.integrity.MediaFileIntegrity;

import tech.kayys.aqli.cli.commands.MediaFileIntegrity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Reads and writes audio metadata sidecars and validates their referenced output artifacts.
 * CLI option interpretation and presentation remain in the command layer.
 */
public final class AudioMetadataSidecarStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AudioMetadataSidecarStore() {
    }

    public static WriteResult write(WriteRequest request) throws Exception {
        Path audioPath = request.audioPath().toAbsolutePath().normalize();
        Path sidecarPath = request.sidecarPath().toAbsolutePath().normalize();
        if (audioPath.equals(sidecarPath)) {
            return new WriteResult(sidecarPath, true);
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

        Files.writeString(sidecarPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        return new WriteResult(sidecarPath, false);
    }

    public static VerificationResult verify(Path sidecarPath, Path outputOverride) throws Exception {
        requireRegularFile(sidecarPath, "audio metadata file not found: ");
        JsonNode root = readObject(sidecarPath, "invalid audio metadata file: ");

        String expectedSha256 = text(root, "output_sha256");
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            throw new IllegalArgumentException(
                    "metadata does not contain output_sha256; regenerate the sidecar with a newer Aqli build.");
        }

        Path audioPath = resolveOutputPath(sidecarPath, root, outputOverride);
        requireRegularFile(audioPath, "audio output not found: ");

        long actualBytes = Files.size(audioPath);
        Long expectedBytes = optionalLong(text(root, "output_size_bytes"));
        if (expectedBytes != null && expectedBytes != actualBytes) {
            throw new IllegalArgumentException("Audio metadata verification failed: byte size mismatch. "
                    + "expected: " + expectedBytes + ", actual: " + actualBytes);
        }

        String actualSha256 = MediaFileIntegrity.sha256Hex(audioPath);
        if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
            throw new IllegalArgumentException("Audio metadata verification failed: SHA-256 mismatch. "
                    + "expected: " + expectedSha256 + ", actual: " + actualSha256);
        }
        return new VerificationResult(audioPath.toAbsolutePath().normalize(), actualBytes, actualSha256);
    }

    public static RefreshResult refresh(Path sidecarPath, Path outputOverride) throws Exception {
        requireRegularFile(sidecarPath, "audio metadata file not found: ");
        JsonNode rootNode = readObject(sidecarPath, "invalid audio metadata file: ");
        ObjectNode root = (ObjectNode) rootNode;

        Path audioPath = resolveOutputPath(sidecarPath, root, outputOverride);
        requireRegularFile(audioPath, "audio output not found: ");

        long sizeBytes = Files.size(audioPath);
        String sha256 = MediaFileIntegrity.sha256Hex(audioPath);
        root.put("schema_version", 2);
        root.put("refreshed_at", java.time.Instant.now().toString());
        root.put("output_path", audioPath.toAbsolutePath().normalize().toString());
        root.put("output_size_bytes", sizeBytes);
        root.put("output_sha256", sha256);

        Files.writeString(sidecarPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return new RefreshResult(sidecarPath.toAbsolutePath().normalize(),
                audioPath.toAbsolutePath().normalize(), sizeBytes, sha256);
    }

    private static JsonNode readObject(Path sidecarPath, String errorPrefix) throws Exception {
        JsonNode root = MAPPER.readTree(sidecarPath.toFile());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(errorPrefix + sidecarPath);
        }
        return root;
    }

    private static Path resolveOutputPath(Path sidecarPath, JsonNode root, Path outputOverride) {
        if (outputOverride != null) {
            return outputOverride.toAbsolutePath().normalize();
        }
        String storedOutputPath = text(root, "output_path");
        if (storedOutputPath == null || storedOutputPath.isBlank()) {
            throw new IllegalArgumentException(
                    "metadata does not contain output_path; pass --output <audio-file> to verify manually");
        }
        Path outputPath = Path.of(storedOutputPath);
        if (!outputPath.isAbsolute()) {
            Path parent = sidecarPath.toAbsolutePath().normalize().getParent();
            outputPath = (parent == null ? outputPath : parent.resolve(outputPath)).normalize();
        }
        return outputPath.toAbsolutePath().normalize();
    }

    private static void requireRegularFile(Path path, String errorPrefix) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(errorPrefix + path);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static Long optionalLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid integer value in replay metadata: " + value);
        }
    }

    public record WriteRequest(
            Path audioPath,
            Path sidecarPath,
            String model,
            String provider,
            String runner,
            String prompt,
            String voiceSelector,
            String audioFormat,
            List<String> replayArgs,
            String replayCommand,
            Map<String, Object> metadata) {
    }

    public record WriteResult(Path sidecarPath, boolean skippedBecausePathsMatch) {
    }

    public record VerificationResult(Path audioPath, long sizeBytes, String sha256) {
    }

    public record RefreshResult(Path sidecarPath, Path audioPath, long sizeBytes, String sha256) {
    }
}

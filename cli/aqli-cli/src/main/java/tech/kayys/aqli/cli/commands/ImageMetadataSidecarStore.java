package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Persists image metadata sidecars and validates the integrity of their output artifacts.
 * CLI option interpretation and presentation remain in the command layer.
 */
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
        payload.put("metadata", request.metadata() == null ? Map.of()
                : new LinkedHashMap<>(request.metadata()));

        Files.writeString(sidecarPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        return new WriteResult(sidecarPath, false);
    }

    static VerificationResult verify(Path sidecarPath, Path outputOverride) throws Exception {
        requireRegularFile(sidecarPath, "Error: image metadata file not found: ");
        JsonNode root = readObject(sidecarPath, "Error: invalid image metadata file: ");

        String expectedSha256 = text(root, "output_sha256");
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            throw new UserMessageException(
                    "Error: metadata does not contain output_sha256; refresh the sidecar with --image-metadata-refresh.");
        }

        Path imagePath = resolveOutputPath(sidecarPath, root, outputOverride);
        requireRegularFile(imagePath, "Error: image output not found: ");

        long actualBytes = Files.size(imagePath);
        Long expectedBytes = optionalLong(text(root, "output_size_bytes"));
        if (expectedBytes != null && expectedBytes != actualBytes) {
            throw new UserMessageException("Image metadata verification failed: byte size mismatch.\n"
                    + "  expected: " + expectedBytes + "\n"
                    + "  actual:   " + actualBytes);
        }

        String actualSha256 = MediaFileIntegrity.sha256Hex(imagePath);
        if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
            throw new UserMessageException("Image metadata verification failed: SHA-256 mismatch.\n"
                    + "  expected: " + expectedSha256 + "\n"
                    + "  actual:   " + actualSha256);
        }
        return new VerificationResult(imagePath.toAbsolutePath().normalize(), actualBytes, actualSha256);
    }

    static RefreshResult refresh(Path sidecarPath, Path outputOverride) throws Exception {
        requireRegularFile(sidecarPath, "image metadata file not found: ");
        JsonNode rootNode = readObject(sidecarPath, "invalid image metadata file: ");
        ObjectNode root = (ObjectNode) rootNode;

        Path imagePath = resolveOutputPath(sidecarPath, root, outputOverride);
        requireRegularFile(imagePath, "image output not found: ");

        long sizeBytes = Files.size(imagePath);
        String sha256 = MediaFileIntegrity.sha256Hex(imagePath);
        root.put("type", "aqli_image_metadata");
        root.put("schema_version", 1);
        root.put("refreshed_at", Instant.now().toString());
        root.put("output_path", imagePath.toAbsolutePath().normalize().toString());
        root.put("output_size_bytes", sizeBytes);
        root.put("output_sha256", sha256);

        Files.writeString(sidecarPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return new RefreshResult(sidecarPath.toAbsolutePath().normalize(),
                imagePath.toAbsolutePath().normalize(), sizeBytes, sha256);
    }

    private static JsonNode readObject(Path sidecarPath, String errorPrefix) throws Exception {
        JsonNode root = MAPPER.readTree(sidecarPath.toFile());
        if (root == null || !root.isObject()) {
            throw new UserMessageException(errorPrefix + sidecarPath);
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
                    "metadata does not contain output_path; pass --output <image-file> to verify manually");
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
            throw new UserMessageException(errorPrefix + path);
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
            throw new IllegalArgumentException("invalid integer value in image metadata: " + value);
        }
    }

    private static void addParameter(java.util.Map<String, Object> payload, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
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
            java.util.Map<String, Object> metadata) {
    }

    static final class UserMessageException extends IllegalArgumentException {
        private final String presentation;

        UserMessageException(String presentation) {
            super(presentation);
            this.presentation = presentation;
        }

        String presentation() {
            return presentation;
        }
    }

    record WriteResult(Path sidecarPath, boolean skippedBecausePathsMatch) {
    }

    record VerificationResult(Path imagePath, long sizeBytes, String sha256) {
    }

    record RefreshResult(Path sidecarPath, Path imagePath, long sizeBytes, String sha256) {
    }
}

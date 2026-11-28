package tech.kayys.aqli.media.metadata;

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
import tech.kayys.aqli.media.integrity.MediaFileIntegrity;

class ImageMetadataSidecarStoreTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesImageMetadataWithIntegrityAndReplayInformation() throws Exception {
        Path image = tempDir.resolve("generated.png");
        Path sidecar = tempDir.resolve("metadata/image.json");
        Files.writeString(image, "image-bytes");
        Map<String, Object> metadata = Map.of("width", 512, "height", 256);
        List<String> replayArgs = List.of("aqli", "run", "--model", "demo");

        ImageMetadataSidecarStore.WriteResult result = ImageMetadataSidecarStore.write(
                new ImageMetadataSidecarStore.WriteRequest(
                        image, sidecar, "demo", "java", "standalone", "draw a tree", "png",
                        "42", 24, 7.5, 512, 256, replayArgs, "aqli run --model demo", metadata));

        assertFalse(result.skippedBecausePathsMatch());
        assertEquals(sidecar.toAbsolutePath().normalize(), result.sidecarPath());
        JsonNode payload = mapper.readTree(sidecar.toFile());
        assertEquals("aqli_image_metadata", payload.path("type").asText());
        assertEquals(1, payload.path("schema_version").asInt());
        assertEquals(image.toAbsolutePath().normalize().toString(), payload.path("output_path").asText());
        assertEquals(Files.size(image), payload.path("output_size_bytes").asLong());
        assertEquals(MediaFileIntegrity.sha256Hex(image), payload.path("output_sha256").asText());
        assertEquals("demo", payload.path("model").asText());
        assertEquals("java", payload.path("provider").asText());
        assertEquals("standalone", payload.path("runner").asText());
        assertEquals("png", payload.path("image_format").asText());
        assertEquals("42", payload.path("seed").asText());
        assertEquals(24, payload.path("steps").asInt());
        assertEquals(7.5, payload.path("guidance_scale").asDouble());
        assertEquals(512, payload.path("width").asInt());
        assertEquals(256, payload.path("height").asInt());
        assertEquals("aqli run --model demo", payload.path("replay_command").asText());
        assertEquals("aqli", payload.path("replay_args").get(0).asText());
        assertEquals(512, payload.path("metadata").path("width").asInt());
    }

    @Test
    void skipsSidecarWhenItWouldOverwriteImageOutput() throws Exception {
        Path image = tempDir.resolve("generated.png");
        Files.writeString(image, "image-bytes");

        ImageMetadataSidecarStore.WriteResult result = ImageMetadataSidecarStore.write(
                new ImageMetadataSidecarStore.WriteRequest(
                        image, image, "demo", "java", null, "prompt", "png", null,
                        null, null, null, null, List.of(), "", Map.of()));

        assertTrue(result.skippedBecausePathsMatch());
        assertEquals(image.toAbsolutePath().normalize(), result.sidecarPath());
        assertEquals("image-bytes", Files.readString(image));
    }

    @Test
    void createsParentDirectoriesAndOmitsBlankOptionalParameters() throws Exception {
        Path image = tempDir.resolve("generated.png");
        Path sidecar = tempDir.resolve("nested/deeper/metadata.json");
        Files.writeString(image, "image-bytes");

        ImageMetadataSidecarStore.write(new ImageMetadataSidecarStore.WriteRequest(
                image, sidecar, null, null, null, null, "png", " ", "", null,
                null, null, List.of(), "", null));

        JsonNode payload = mapper.readTree(sidecar.toFile());
        assertTrue(Files.isRegularFile(sidecar));
        assertFalse(payload.has("seed"));
        assertFalse(payload.has("steps"));
    }

    @Test
    void verifiesOutputIntegrityWithoutCreatingALockFile() throws Exception {
        Path image = tempDir.resolve("generated.png");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(image, "image-bytes");
        Files.writeString(sidecar, """
                {
                  "type": "aqli_image_metadata",
                  "output_path": "generated.png",
                  "output_size_bytes": 11,
                  "output_sha256": "%s"
                }
                """.formatted(MediaFileIntegrity.sha256Hex(image)));

        ImageMetadataSidecarStore.VerificationResult result =
                ImageMetadataSidecarStore.verify(sidecar, null);

        assertEquals(image.toAbsolutePath().normalize(), result.imagePath());
        assertEquals(Files.size(image), result.sizeBytes());
        assertEquals(MediaFileIntegrity.sha256Hex(image), result.sha256());
        assertFalse(Files.exists(tempDir.resolve(".metadata.json.lock")),
                "read-only verification must not create a lock file");
    }

    @Test
    void preservesExistingMetadataWhenRefreshingIntegrityFields() throws Exception {
        Path image = tempDir.resolve("generated.png");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(image, "original-image");
        String originalDigest = MediaFileIntegrity.sha256Hex(image);
        Files.writeString(sidecar, """
                {
                  "type": "legacy-image-metadata",
                  "schema_version": 0,
                  "output_path": "generated.png",
                  "output_size_bytes": 14,
                  "output_sha256": "%s",
                  "model": "keep-image-model",
                  "provider": "keep-provider",
                  "prompt": "keep image prompt",
                  "image_format": "webp",
                  "seed": "keep-seed",
                  "steps": 31,
                  "guidance_scale": 6.5,
                  "width": 768,
                  "height": 512,
                  "replay_args": ["run", "--model", "keep-image-model"],
                  "metadata": {"custom": "keep-image-metadata"}
                }
                """.formatted(originalDigest));
        Files.writeString(image, "replacement-image-content");

        ImageMetadataSidecarStore.RefreshResult result =
                ImageMetadataSidecarStore.refresh(sidecar, null);

        JsonNode payload = mapper.readTree(sidecar.toFile());
        String refreshedDigest = MediaFileIntegrity.sha256Hex(image);
        assertEquals("aqli_image_metadata", payload.path("type").asText());
        assertEquals(1, payload.path("schema_version").asInt());
        assertEquals("keep-image-model", payload.path("model").asText());
        assertEquals("keep-provider", payload.path("provider").asText());
        assertEquals("keep image prompt", payload.path("prompt").asText());
        assertEquals("webp", payload.path("image_format").asText());
        assertEquals("keep-seed", payload.path("seed").asText());
        assertEquals(31, payload.path("steps").asInt());
        assertEquals(6.5, payload.path("guidance_scale").asDouble());
        assertEquals(768, payload.path("width").asInt());
        assertEquals(512, payload.path("height").asInt());
        assertEquals("run", payload.path("replay_args").get(0).asText());
        assertEquals("keep-image-metadata", payload.path("metadata").path("custom").asText());
        assertTrue(payload.hasNonNull("refreshed_at"));
        assertEquals(image.toAbsolutePath().normalize().toString(), payload.path("output_path").asText());
        assertEquals(Files.size(image), payload.path("output_size_bytes").asLong());
        assertEquals(refreshedDigest, payload.path("output_sha256").asText());
        assertFalse(originalDigest.equals(refreshedDigest));
        assertEquals(refreshedDigest, result.sha256());
    }

    @Test
    void leavesExistingSidecarUnchangedWhenRefreshCannotFindOutput() throws Exception {
        Path sidecar = tempDir.resolve("metadata.json");
        String original = """
                {"type":"aqli_image_metadata","output_path":"missing.png","output_sha256":"original"}
                """;
        Files.writeString(sidecar, original);

        assertThrows(ImageMetadataSidecarStore.ImageMetadataSidecarException.class,
                () -> ImageMetadataSidecarStore.refresh(sidecar, null));

        assertEquals(original, Files.readString(sidecar));
    }
}

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
}

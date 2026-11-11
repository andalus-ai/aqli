package tech.kayys.aqli.media.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aqli.media.integrity.MediaFileIntegrity;

class ImageMetadataSidecarStoreIntegrityTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void verifiesOutputSizeAndDigestAndSupportsExplicitOutputOverride() throws Exception {
        Path image = tempDir.resolve("actual.png");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(image, "verified-image");
        Files.writeString(sidecar, """
                {
                  "type": "aqli_image_metadata",
                  "output_path": "missing.png",
                  "output_size_bytes": 14,
                  "output_sha256": "%s"
                }
                """.formatted(MediaFileIntegrity.sha256Hex(image)));

        var result = ImageMetadataSidecarStore.verify(sidecar, image);

        assertEquals(image.toAbsolutePath().normalize(), result.imagePath());
        assertEquals(Files.size(image), result.sizeBytes());
        assertEquals(MediaFileIntegrity.sha256Hex(image), result.sha256());
    }

    @Test
    void resolvesRelativeOutputPathsAgainstSidecarDirectory() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("sidecars"));
        Path image = directory.resolve("image.png");
        Path sidecar = directory.resolve("image.json");
        Files.writeString(image, "relative-image");
        Files.writeString(sidecar, """
                {"output_path":"image.png","output_sha256":"%s"}
                """.formatted(MediaFileIntegrity.sha256Hex(image)));

        var result = ImageMetadataSidecarStore.verify(sidecar, null);

        assertEquals(image.toAbsolutePath().normalize(), result.imagePath());
    }

    @Test
    void rejectsMissingDigestAndMismatchedOutput() throws Exception {
        Path image = tempDir.resolve("image.png");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(image, "image-bytes");
        Files.writeString(sidecar, """
                {"output_path":"image.png"}
                """);
        assertThrows(IllegalArgumentException.class,
                () -> ImageMetadataSidecarStore.verify(sidecar, null));

        Files.writeString(sidecar, """
                {"output_path":"image.png","output_sha256":"0000"}
                """);
        assertThrows(IllegalArgumentException.class,
                () -> ImageMetadataSidecarStore.verify(sidecar, null));
    }

    @Test
    void rejectsOutputSizeMismatchEvenWhenDigestMatches() throws Exception {
        Path image = tempDir.resolve("image.png");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(image, "image-bytes");
        Files.writeString(sidecar, """
                {"output_path":"image.png","output_size_bytes":1,"output_sha256":"%s"}
                """.formatted(MediaFileIntegrity.sha256Hex(image)));

        assertThrows(IllegalArgumentException.class,
                () -> ImageMetadataSidecarStore.verify(sidecar, null));
    }

    @Test
    void refreshesIntegrityFieldsWithoutDiscardingExistingMetadata() throws Exception {
        Path image = tempDir.resolve("image.png");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(image, "updated-image");
        Files.writeString(sidecar, """
                {"type":"aqli_image_metadata","schema_version":1,"output_path":"image.png","prompt":"keep me"}
                """);

        var result = ImageMetadataSidecarStore.refresh(sidecar, null);
        JsonNode payload = mapper.readTree(sidecar.toFile());

        assertEquals(sidecar.toAbsolutePath().normalize(), result.sidecarPath());
        assertEquals(image.toAbsolutePath().normalize(), result.imagePath());
        assertEquals(1, payload.path("schema_version").asInt());
        assertTrue(payload.hasNonNull("refreshed_at"));
        assertEquals("keep me", payload.path("prompt").asText());
        assertEquals(Files.size(image), payload.path("output_size_bytes").asLong());
        assertEquals(MediaFileIntegrity.sha256Hex(image), payload.path("output_sha256").asText());
    }

    @Test
    void refreshSupportsExplicitOutputOverride() throws Exception {
        Path image = tempDir.resolve("override.png");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(image, "override-image");
        Files.writeString(sidecar, """
                {"output_path":"missing.png"}
                """);

        var result = ImageMetadataSidecarStore.refresh(sidecar, image);

        assertEquals(image.toAbsolutePath().normalize(), result.imagePath());
    }
}

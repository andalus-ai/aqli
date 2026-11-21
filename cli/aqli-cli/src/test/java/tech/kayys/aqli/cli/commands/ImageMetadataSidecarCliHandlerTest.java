package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageMetadataSidecarCliHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void writesVerifiesAndRefreshesSidecarUsingOutputOverride() throws Exception {
        Path image = tempDir.resolve("generated.png");
        Path override = tempDir.resolve("replacement.png");
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(image, "image-payload");
        Files.writeString(override, "image-payload");

        ImageMetadataSidecarCliHandler.write(new MediaMetadataSidecarCliHandler.ImageSidecarRequest(
                image, sidecar.toString(), true, "model", "provider", "runner",
                "prompt", "42", 24, 7.5, 512, 256, Map.of("width", 512)));

        assertTrue(Files.isRegularFile(sidecar));
        assertTrue(ImageMetadataSidecarCliHandler.verify(sidecar.toString(), override.toString()));
        assertTrue(ImageMetadataSidecarCliHandler.refresh(sidecar.toString(), override.toString()));
        assertTrue(ImageMetadataSidecarCliHandler.verify(sidecar.toString(), override.toString()));
    }

    @Test
    void doesNotOverwriteImageWhenSidecarPathMatchesOutput() throws Exception {
        Path image = tempDir.resolve("generated.png");
        Files.writeString(image, "image-payload");

        ImageMetadataSidecarCliHandler.write(new MediaMetadataSidecarCliHandler.ImageSidecarRequest(
                image, image.toString(), true, "model", "provider", "runner",
                "prompt", "42", 24, 7.5, 512, 256, Map.of()));

        assertEquals("image-payload", Files.readString(image));
    }

    @Test
    void skipsWritingWhenNeitherFlagNorExplicitPathIsProvided() throws Exception {
        Path image = tempDir.resolve("generated.png");
        Files.writeString(image, "image-payload");
        Path implicitSidecar = tempDir.resolve("generated.json");

        ImageMetadataSidecarCliHandler.write(new MediaMetadataSidecarCliHandler.ImageSidecarRequest(
                image, null, false, "model", "provider", "runner",
                "prompt", null, null, null, null, null, Map.of()));

        assertFalse(Files.exists(implicitSidecar));
    }
    @Test
    void returnsFalseWhenVerificationOrRefreshSidecarDoesNotExist() {
        Path missingSidecar = tempDir.resolve("missing-image.json");

        assertFalse(ImageMetadataSidecarCliHandler.verify(missingSidecar.toString(), null));
        assertFalse(ImageMetadataSidecarCliHandler.refresh(missingSidecar.toString(), null));
    }
}

package tech.kayys.aqli.media.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaMetadataSidecarFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void createsParentDirectoriesAndReplacesExistingSidecar() throws Exception {
        Path sidecar = tempDir.resolve("nested/metadata.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, "old-content");

        MediaMetadataSidecarFiles.writeAtomically(sidecar, "{\"schema_version\":1}");

        assertEquals("{\"schema_version\":1}", Files.readString(sidecar));
        try (Stream<Path> files = Files.list(sidecar.getParent())) {
            assertEquals(1, files.count(), "temporary file should be moved away");
        }
    }

    @Test
    void removesTemporaryFileWhenDestinationCannotBeReplaced() throws Exception {
        Path destinationDirectory = tempDir.resolve("metadata.json");
        Files.createDirectory(destinationDirectory);

        assertThrows(IOException.class,
                () -> MediaMetadataSidecarFiles.writeAtomically(destinationDirectory, "content"));

        assertTrue(Files.isDirectory(destinationDirectory));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(1, files.count(), "failed write should not leave a temporary file");
        }
    }

    @Test
    void rejectsNullInputsBeforeCreatingDirectories() {
        Path sidecar = tempDir.resolve("not-created/metadata.json");

        assertThrows(NullPointerException.class,
                () -> MediaMetadataSidecarFiles.writeAtomically(sidecar, null));
        assertThrows(NullPointerException.class,
                () -> MediaMetadataSidecarFiles.writeAtomically(null, "content"));

        assertFalse(Files.exists(sidecar.getParent()));
    }

    @Test
    void writesUtf8ContentBeforeReplacingDestination() throws Exception {
        Path sidecar = tempDir.resolve("metadata.json");
        String content = "{\"prompt\":\"こんにちは 🌳\"}";

        MediaMetadataSidecarFiles.writeAtomically(sidecar, content);

        assertEquals(content, Files.readString(sidecar));
    }
}

package tech.kayys.aqli.model.repo.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalModelFileInventoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void measuresFileAndRecursiveDirectorySizes() throws Exception {
        Path nested = Files.createDirectories(temporaryDirectory.resolve("nested"));
        Files.write(temporaryDirectory.resolve("first.bin"), new byte[] { 1, 2, 3 });
        Files.write(nested.resolve("second.bin"), new byte[] { 4, 5 });

        assertEquals(3L, LocalModelFileInventory.sizeBytes(temporaryDirectory.resolve("first.bin")));
        assertEquals(5L, LocalModelFileInventory.sizeBytes(nested.resolve("second.bin")));
        assertEquals(5L, LocalModelFileInventory.sizeBytes(temporaryDirectory));
    }

    @Test
    void findsLatestModificationAcrossDirectoryTree() throws Exception {
        Path first = temporaryDirectory.resolve("first.bin");
        Path nested = Files.createDirectories(temporaryDirectory.resolve("nested"));
        Path second = nested.resolve("second.bin");
        Files.write(first, new byte[] { 1 });
        Files.write(second, new byte[] { 2 });

        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        Instant newer = Instant.parse("2026-02-01T00:00:00Z");
        Files.setLastModifiedTime(first, FileTime.from(older));
        Files.setLastModifiedTime(second, FileTime.from(newer));

        assertEquals(newer, LocalModelFileInventory.latestModified(temporaryDirectory));
        assertEquals(older, LocalModelFileInventory.latestModified(first));
    }

    @Test
    void returnsBestEffortDefaultsForMissingAndEmptyPaths() throws Exception {
        Path missing = temporaryDirectory.resolve("missing");
        assertEquals(0L, LocalModelFileInventory.sizeBytes(missing));
        assertEquals(Instant.EPOCH, LocalModelFileInventory.latestModified(missing));
        assertEquals(0L, LocalModelFileInventory.sizeBytes(temporaryDirectory));
        assertEquals(Instant.EPOCH, LocalModelFileInventory.latestModified(temporaryDirectory));
    }
}

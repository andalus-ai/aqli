package tech.kayys.aqli.model.repo.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalModelFileInventoryTest {

    @TempDir
    Path temporaryDirectory;

    private final LocalModelFileInventory inventory = new LocalModelFileInventory();

    @Test
    void discoversSupportedWeightFilesAndRetainsFormatFolderContext() throws Exception {
        Path base = temporaryDirectory.resolve("gguf");
        Files.createDirectories(base.resolve("org/model"));
        Files.writeString(base.resolve("org/model/model.gguf"), "weights");
        Files.writeString(base.resolve("org/model/config.json"), "{}");
        Files.writeString(base.resolve("org/model/tokenizer"), "tokens");

        List<LocalModelFileInventory.Candidate> candidates = inventory.discover(base, "gguf", true);

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().allMatch(candidate -> candidate.base().equals(base)));
        assertTrue(candidates.stream().allMatch(candidate -> "gguf".equals(candidate.fallbackFormat())));
        assertTrue(candidates.stream().allMatch(LocalModelFileInventory.Candidate::runnable));
        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.file().getFileName().toString().equals("model.gguf")));
        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.file().getFileName().toString().equals("tokenizer")));
    }

    @Test
    void skipsHiddenFilesAndFilesOutsideLegacyDepthLimit() throws Exception {
        Path base = temporaryDirectory.resolve("safetensors");
        Files.createDirectories(base.resolve("one/two/three"));
        Files.createDirectories(base.resolve("one/two/three/four"));
        Files.writeString(base.resolve(".hidden.gguf"), "hidden");
        Files.writeString(base.resolve("one/two/three/model.safetensors"), "included");
        Files.writeString(base.resolve("one/two/three/four/deep.gguf"), "too deep");

        List<LocalModelFileInventory.Candidate> candidates = inventory.discover(base, "safetensors", false);

        assertEquals(1, candidates.size());
        assertEquals("model.safetensors", candidates.get(0).file().getFileName().toString());
        assertFalse(candidates.get(0).runnable());
    }

    @Test
    void missingFormatDirectoryProducesEmptyInventory() {
        assertTrue(inventory.discover(temporaryDirectory.resolve("missing"), "onnx", true).isEmpty());
    }

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

        java.time.Instant older = java.time.Instant.parse("2026-01-01T00:00:00Z");
        java.time.Instant newer = java.time.Instant.parse("2026-02-01T00:00:00Z");
        Files.setLastModifiedTime(first, java.nio.file.attribute.FileTime.from(older));
        Files.setLastModifiedTime(second, java.nio.file.attribute.FileTime.from(newer));

        assertEquals(newer, LocalModelFileInventory.latestModified(temporaryDirectory));
        assertEquals(older, LocalModelFileInventory.latestModified(first));
    }

    @Test
    void returnsBestEffortDefaultsForMissingAndEmptyPaths() {
        Path missing = temporaryDirectory.resolve("missing");
        assertEquals(0L, LocalModelFileInventory.sizeBytes(missing));
        assertEquals(java.time.Instant.EPOCH, LocalModelFileInventory.latestModified(missing));
        assertEquals(0L, LocalModelFileInventory.sizeBytes(temporaryDirectory));
        assertEquals(java.time.Instant.EPOCH, LocalModelFileInventory.latestModified(temporaryDirectory));
    }

}

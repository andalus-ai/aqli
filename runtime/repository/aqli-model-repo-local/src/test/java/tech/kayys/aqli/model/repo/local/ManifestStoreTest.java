package tech.kayys.aqli.model.repo.local;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndFindsManifestByModelAndRevision() {
        ManifestStore store = new ManifestStore(temporaryDirectory.resolve("manifests"));
        AqliManifest manifest = new AqliManifest();
        manifest.setId(AqliManifest.computeName("org/model", "main"));
        manifest.setModelId("org/model");
        manifest.setRevision("main");
        manifest.setName(manifest.getId());
        manifest.setFormat("SAFETENSORS");
        manifest.setBlobPath(temporaryDirectory.resolve("model.safetensors").toString());
        manifest.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        manifest.setFiles(List.of("model.safetensors", "config.json"));
        manifest.setSizeBytes(42);

        store.save(manifest);

        AqliManifest restored = store.findByModelId("org/model", "main").orElseThrow();
        assertEquals(manifest.getId(), restored.getId());
        assertEquals("main", restored.getRevision());
        assertEquals(List.of("model.safetensors", "config.json"), restored.getFiles());
        assertEquals(42, restored.getSizeBytes());
        assertTrue(store.findByModelId("org/model", "dev").isEmpty());
    }

    @Test
    void computesStableDistinctNamesForRevisions() {
        assertEquals(AqliManifest.computeName("org/model", "main"),
                AqliManifest.computeName("org/model", "main"));
        assertNotEquals(AqliManifest.computeName("org/model", "main"),
                AqliManifest.computeName("org/model", "dev"));
    }

    @Test
    void listsFilesRelativeToBlobDirectory() throws Exception {
        Path blobDir = temporaryDirectory.resolve("blob");
        Files.createDirectories(blobDir.resolve("config"));
        Files.writeString(blobDir.resolve("config/model.json"), "{}");
        Files.writeString(blobDir.resolve("weights.safetensors"), "weights");

        assertEquals(List.of("config/model.json", "weights.safetensors"),
                ManifestStore.listBlobFiles(blobDir));
    }

    @Test
    void blobPathCannotEscapeCacheRoot() {
        Path resolved = ManifestStore.resolveBlobDir("../../outside", "manifest");
        Path cacheRoot = Path.of(System.getProperty("user.home"), ".aqli", "models").toAbsolutePath().normalize();
        assertTrue(resolved.normalize().startsWith(cacheRoot));
    }
}

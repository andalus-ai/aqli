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
        manifest.setUpdatedAt("2026-01-02T00:00:00Z");
        manifest.setFiles(List.of("model.safetensors", "config.json"));
        manifest.setSizeBytes(42);

        store.save(manifest);

        AqliManifest restored = store.findByModelId("org/model", "main").orElseThrow();
        assertEquals(manifest.getId(), restored.getId());
        assertEquals("main", restored.getRevision());
        assertEquals(List.of("model.safetensors", "config.json"), restored.getFiles());
        assertEquals(42, restored.getSizeBytes());
        assertEquals("2026-01-02T00:00:00Z", restored.getUpdatedAt());
        assertTrue(store.findByModelId("org/model", "dev").isEmpty());
    }


    @Test
    void reportsMalformedManifestFilesWithoutHidingReadableManifests() throws Exception {
        Path manifestDirectory = temporaryDirectory.resolve("manifests");
        ManifestStore store = new ManifestStore(manifestDirectory);
        AqliManifest valid = new AqliManifest();
        valid.setId("valid-manifest");
        valid.setModelId("org/model");
        valid.setRevision("main");
        valid.setName("Model");
        store.save(valid);
        Files.writeString(manifestDirectory.resolve("broken.json"), "{ invalid json");

        ManifestStore.ManifestListing listing = store.listAllWithDiagnostics();

        assertEquals(1, listing.manifests().size());
        assertEquals("valid-manifest", listing.manifests().get(0).getId());
        assertEquals(1, listing.issues().size());
        assertEquals("broken.json", listing.issues().get(0).fileName());
        assertFalse(listing.issues().get(0).message().isBlank());
        // Existing callers retain the resilient list-only behavior.
        assertEquals(1, store.listAll().size());
    }

    @Test
    void returnsEmptyDiagnosticsWhenManifestDirectoryDoesNotExist() {
        ManifestStore store = new ManifestStore(temporaryDirectory.resolve("missing"));

        ManifestStore.ManifestListing listing = store.listAllWithDiagnostics();

        assertTrue(listing.manifests().isEmpty());
        assertTrue(listing.issues().isEmpty());
    }

    @Test
    void failsWhenManifestLocationIsNotADirectory() throws Exception {
        Path manifestLocation = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(manifestLocation, "not a directory");

        ManifestStore store = new ManifestStore(manifestLocation);

        assertThrows(IllegalStateException.class, store::listAll);
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
    void detectsManifestBlobFormatThroughSharedInventoryMapping() throws Exception {
        Path ggufBlob = temporaryDirectory.resolve("gguf-blob");
        Files.createDirectories(ggufBlob.resolve("nested"));
        Files.writeString(ggufBlob.resolve("nested/model.GGUF"), "weights");
        assertEquals("gguf", ManifestStore.detectFormat(ggufBlob));

        Path liteRtBlob = temporaryDirectory.resolve("litert-blob");
        Files.createDirectories(liteRtBlob);
        Files.writeString(liteRtBlob.resolve("model.task"), "task");
        assertEquals("litert", ManifestStore.detectFormat(liteRtBlob));

        Path torchScriptBlob = temporaryDirectory.resolve("torchscript-blob");
        Files.createDirectories(torchScriptBlob);
        Files.writeString(torchScriptBlob.resolve("model.pth"), "weights");
        assertEquals("torchscript", ManifestStore.detectFormat(torchScriptBlob));

        Path unknownBlob = temporaryDirectory.resolve("unknown-blob");
        Files.createDirectories(unknownBlob);
        Files.writeString(unknownBlob.resolve("config.json"), "{}");
        assertEquals("unknown", ManifestStore.detectFormat(unknownBlob));
    }

    @Test
    void blobPathCannotEscapeCacheRoot() {
        Path resolved = ManifestStore.resolveBlobDir("../../outside", "manifest");
        Path cacheRoot = Path.of(System.getProperty("user.home"), ".aqli", "models").toAbsolutePath().normalize();
        assertTrue(resolved.normalize().startsWith(cacheRoot));
    }
}

package tech.kayys.aqli.model.repo.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tech.kayys.aljabr.core.model.ModelFormat;
import tech.kayys.aljabr.spi.model.ArtifactLocation;
import tech.kayys.aljabr.spi.model.ModelManifest;
import tech.kayys.aljabr.spi.model.ModelRef;
import tech.kayys.aljabr.spi.model.Pageable;

class LocalModelRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesListsAndFindsLocalManifestWithoutCopyingWeights() throws Exception {
        Path weights = temporaryDirectory.resolve("model.safetensors");
        Files.writeString(weights, "weights");
        ManifestStore store = new ManifestStore(temporaryDirectory.resolve("manifests"));
        LocalModelRepository repository = new LocalModelRepository(store);
        ModelManifest manifest = manifest("org/model", weights);

        repository.save(manifest).await().indefinitely();

        ModelManifest found = repository.findById("org/model", "tenant-a").await().indefinitely();
        assertNotNull(found);
        assertEquals("org/model", found.modelId());
        assertEquals("tenant-a", found.requestId());
        assertTrue(found.supportsFormat(ModelFormat.SAFETENSORS));
        assertEquals(weights.toAbsolutePath().toString(), Path.of(found.path()).toString());

        List<ModelManifest> page = repository.list("tenant-a", Pageable.of(0, 10)).await().indefinitely();
        assertEquals(1, page.size());
        assertEquals("org/model", page.getFirst().modelId());
        assertTrue(Files.exists(weights));
    }

    @Test
    void resolvesAndFetchesOnlyExistingLocalPaths() throws Exception {
        Path weights = temporaryDirectory.resolve("model.gguf");
        Files.writeString(weights, "gguf");
        LocalModelRepository repository = new LocalModelRepository(
                new ManifestStore(temporaryDirectory.resolve("manifests")));

        ModelRef ref = new ModelRef("local", null, weights.toString(), null, Map.of());
        var descriptor = repository.resolve(ref);

        assertEquals("gguf", descriptor.format());
        assertEquals(weights.toAbsolutePath(), repository.fetch(descriptor).path());
        assertTrue(repository.supports(ref));
        assertFalse(repository.supports(new ModelRef("hf", null, "org/model", null, Map.of())));
    }

    @Test
    void deletingManifestDoesNotDeleteUserOwnedWeights() throws Exception {
        Path weights = temporaryDirectory.resolve("model.onnx");
        Files.writeString(weights, "onnx");
        ManifestStore store = new ManifestStore(temporaryDirectory.resolve("manifests"));
        LocalModelRepository repository = new LocalModelRepository(store);
        repository.save(manifest("org/model", weights)).await().indefinitely();

        repository.delete("org/model", "tenant-a").await().indefinitely();

        assertTrue(store.listAll().isEmpty());
        assertTrue(Files.exists(weights));
    }

    @Test
    void rejectsInvalidPagination() {
        LocalModelRepository repository = new LocalModelRepository(
                new ManifestStore(temporaryDirectory.resolve("manifests")));

        assertThrows(IllegalArgumentException.class,
                () -> repository.list("tenant-a", Pageable.of(-1, 10)).await().indefinitely());
    }

    private static ModelManifest manifest(String modelId, Path weights) {
        return ModelManifest.builder()
                .modelId(modelId)
                .name("Local test model")
                .version("main")
                .path(weights.toAbsolutePath().toString())
                .apiKey("community")
                .requestId("tenant-a")
                .artifacts(Map.of(ModelFormat.SAFETENSORS,
                        new ArtifactLocation(weights.toUri().toString(), null, 7L, "application/octet-stream")))
                .metadata(Map.of("source", "test"))
                .build();
    }
}

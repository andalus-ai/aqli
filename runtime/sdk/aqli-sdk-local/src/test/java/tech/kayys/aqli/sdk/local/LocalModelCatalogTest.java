package tech.kayys.aqli.sdk.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.model.repo.local.AqliManifest;
import tech.kayys.aqli.model.repo.local.ManifestStore;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aljabr.spi.model.ModelInfo;

class LocalModelCatalogTest {

    @Test
    void listsModelsInStableOrderAndAppliesOffsetAndLimit() throws Exception {
        LocalModelCatalog catalog = catalog(
                manifest("b/model", "bbbbbbbbbbbbbbbbbbbbbbbb", "main", "B", Instant.parse("2026-01-01T00:00:00Z")),
                manifest("a/model", "aaaaaaaaaaaaaaaaaaaaaaaa", "dev", "A dev", Instant.parse("2026-01-02T00:00:00Z")),
                manifest("a/model", "cccccccccccccccccccccccc", "main", "A main", Instant.parse("2026-01-03T00:00:00Z")));

        List<ModelInfo> page = catalog.listModels(1, 1);

        assertEquals(1, page.size());
        assertEquals("a/model", page.get(0).getModelId());
        assertEquals("main", page.get(0).getVersion());
        assertTrue(catalog.listModels(10, 5).isEmpty());
    }

    @Test
    void rejectsInvalidPagination() {
        LocalModelCatalog catalog = catalog();

        SdkException error = assertThrows(SdkException.class, () -> catalog.listModels(-1, 10));

        assertEquals("SDK_ERR_INVALID_ARGUMENT", error.getErrorCode());
    }

    @Test
    void resolvesExactManifestIdToItsPersistedRevision() throws Exception {
        LocalModelCatalog catalog = catalog(
                manifest("org/model", "aaaaaaaaaaaaaaaaaaaaaaaa", "main", "Model", Instant.parse("2026-01-01T00:00:00Z")),
                manifest("org/model", "bbbbbbbbbbbbbbbbbbbbbbbb", "dev", "Model", Instant.parse("2026-01-02T00:00:00Z")));

        ModelInfo info = catalog.findModel("aaaaaaaaaaaaaaaaaaaaaaaa").orElseThrow();

        assertEquals("org/model", info.getModelId());
        assertEquals("main", info.getVersion());
        assertEquals("aaaaaaaa", info.getShortId());
    }

    @Test
    void defaultsMissingRevisionToMain() throws Exception {
        AqliManifest stored = manifest(
                "org/model", "aaaaaaaaaaaaaaaaaaaaaaaa", null, "Model", Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals("main", catalog(stored).findModel("org/model").orElseThrow().getVersion());
    }

    @Test
    void exposesMalformedManifestDiagnostics() throws Exception {
        ManifestStore store = mock(ManifestStore.class);
        ManifestStore.ManifestReadIssue issue =
                new ManifestStore.ManifestReadIssue("broken.json", "invalid JSON");
        when(store.listAllWithDiagnostics()).thenReturn(
                new ManifestStore.ManifestListing(List.of(), List.of(issue)));

        LocalModelCatalog catalog = new LocalModelCatalog(store);

        assertEquals(List.of(issue), catalog.listReadIssues());
    }

    @Test
    void wrapsManifestStoreFailuresAsSdkErrors() {
        ManifestStore store = mock(ManifestStore.class);
        when(store.listAllWithDiagnostics()).thenThrow(new IllegalStateException("disk unavailable"));
        LocalModelCatalog catalog = new LocalModelCatalog(store);

        SdkException error = assertThrows(SdkException.class, () -> catalog.listModels(0, 10));

        assertEquals("SDK_ERR_MODEL_CATALOG", error.getErrorCode());
    }

    @Test
    void resolvesLogicalModelIdToNewestRevision() throws Exception {
        LocalModelCatalog catalog = catalog(
                manifest("org/model", "aaaaaaaaaaaaaaaaaaaaaaaa", "main", "Model", Instant.parse("2026-01-01T00:00:00Z")),
                manifest("org/model", "bbbbbbbbbbbbbbbbbbbbbbbb", "dev", "Model", Instant.parse("2026-01-02T00:00:00Z")));

        assertEquals("dev", catalog.findModel("org/model").orElseThrow().getVersion());
    }

    @Test
    void rejectsAmbiguousManifestIdPrefixes() {
        LocalModelCatalog catalog = catalog(
                manifest("org/one", "abcdef001111111111111111", "main", "One", Instant.parse("2026-01-01T00:00:00Z")),
                manifest("org/two", "abcdef002222222222222222", "main", "Two", Instant.parse("2026-01-02T00:00:00Z")));

        SdkException error = assertThrows(SdkException.class, () -> catalog.findModel("abcdef00"));

        assertEquals("SDK_ERR_MODEL_AMBIGUOUS", error.getErrorCode());
    }

    @Test
    void preservesPersistedMetadataWithoutInventingUpdatedAt() throws Exception {
        AqliManifest stored = manifest("org/model", "aaaaaaaaaaaaaaaaaaaaaaaa", "main", "Model",
                Instant.parse("2026-01-01T00:00:00Z"));
        stored.setFormat("gguf");
        stored.setSizeBytes(1234);
        stored.setArchitecture("llama");
        stored.setBlobPath("/models/model.gguf");
        stored.setSource("local");
        stored.setFiles(List.of("model.gguf"));
        stored.setMetadata(Map.of("quantization", "Q4_K_M", "custom", "value"));
        LocalModelCatalog catalog = catalog(stored);

        ModelInfo info = catalog.findModel("org/model").orElseThrow();

        assertEquals("gguf", info.getFormat());
        assertEquals(1234L, info.getSizeBytes());
        assertEquals("llama", info.getArchitecture());
        assertEquals("Q4_K_M", info.getMetadata().get("quantization"));
        assertEquals("value", info.getMetadata().get("custom"));
        assertEquals("/models/model.gguf", info.getMetadata().get("blobPath"));
        assertEquals(List.of("model.gguf"), info.getMetadata().get("files"));
        assertNull(info.getUpdatedAt());
    }

    @Test
    void returnsEmptyForUnknownOrBlankIdentifiers() throws Exception {
        LocalModelCatalog catalog = catalog(manifest(
                "org/model", "aaaaaaaaaaaaaaaaaaaaaaaa", "main", "Model", Instant.parse("2026-01-01T00:00:00Z")));

        assertEquals(Optional.empty(), catalog.findModel("missing"));
        assertEquals(Optional.empty(), catalog.findModel(" "));
    }

    private static LocalModelCatalog catalog(AqliManifest... entries) {
        ManifestStore store = mock(ManifestStore.class);
        when(store.listAll()).thenReturn(List.of(entries));
        return new LocalModelCatalog(store);
    }

    private static AqliManifest manifest(
            String modelId, String id, String revision, String name, Instant createdAt) {
        AqliManifest manifest = new AqliManifest();
        manifest.setModelId(modelId);
        manifest.setId(id);
        manifest.setRevision(revision);
        manifest.setName(name);
        manifest.setCreatedAt(createdAt);
        manifest.setEnabled(true);
        return manifest;
    }
}

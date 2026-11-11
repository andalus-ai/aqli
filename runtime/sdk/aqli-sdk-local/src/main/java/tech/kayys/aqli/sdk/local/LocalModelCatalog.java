package tech.kayys.aqli.sdk.local;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tech.kayys.aqli.model.repo.local.AqliManifest;
import tech.kayys.aqli.model.repo.local.ManifestStore;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aljabr.spi.model.ModelInfo;

/**
 * Read-only view of the machine-local model manifest catalogue.
 *
 * <p>The manifest ID identifies one persisted model revision. The model ID
 * identifies the logical repository model and may therefore have multiple
 * revisions. A lookup by logical model ID selects the most recently
 * registered revision; a lookup by manifest ID or an unambiguous manifest-ID
 * prefix selects that exact revision.</p>
 */
@ApplicationScoped
public class LocalModelCatalog {

    private static final Comparator<AqliManifest> CATALOG_ORDER =
            Comparator.comparing(LocalModelCatalog::modelId, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(LocalModelCatalog::revision, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(LocalModelCatalog::manifestId, String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<AqliManifest> NEWEST_REVISION_FIRST =
            Comparator.comparing(AqliManifest::getCreatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(LocalModelCatalog::revision, String.CASE_INSENSITIVE_ORDER.reversed())
                    .thenComparing(LocalModelCatalog::manifestId, String.CASE_INSENSITIVE_ORDER);

    private final ManifestStore manifests;

    @Inject
    public LocalModelCatalog(ManifestStore manifests) {
        this.manifests = Objects.requireNonNull(manifests, "manifests");
    }

    public List<ModelInfo> listModels(int offset, int limit) throws SdkException {
        if (offset < 0 || limit < 1) {
            throw new SdkException("SDK_ERR_INVALID_ARGUMENT",
                    "Model catalogue offset must be >= 0 and limit must be > 0.");
        }

        List<AqliManifest> ordered = manifests.listAll().stream()
                .filter(LocalModelCatalog::hasIdentity)
                .sorted(CATALOG_ORDER)
                .toList();
        if (offset >= ordered.size()) {
            return List.of();
        }

        long end = Math.min((long) ordered.size(), (long) offset + limit);
        return ordered.subList(offset, (int) end).stream()
                .map(LocalModelCatalog::toModelInfo)
                .toList();
    }

    public Optional<ModelInfo> findModel(String identifier) throws SdkException {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        String requested = identifier.trim();
        List<AqliManifest> all = manifests.listAll().stream()
                .filter(LocalModelCatalog::hasIdentity)
                .toList();

        Optional<AqliManifest> exactManifest = all.stream()
                .filter(manifest -> requested.equalsIgnoreCase(manifest.getId()))
                .findFirst();
        if (exactManifest.isPresent()) {
            return exactManifest.map(LocalModelCatalog::toModelInfo);
        }

        List<AqliManifest> prefixMatches = all.stream()
                .filter(manifest -> manifest.getId().regionMatches(
                        true, 0, requested, 0, requested.length()))
                .toList();
        if (!prefixMatches.isEmpty()) {
            return Optional.of(toModelInfo(requireUnambiguousPrefix(requested, prefixMatches)));
        }

        List<AqliManifest> modelMatches = all.stream()
                .filter(manifest -> requested.equalsIgnoreCase(manifest.getModelId()))
                .toList();
        if (!modelMatches.isEmpty()) {
            return Optional.of(toModelInfo(newestRevision(modelMatches)));
        }

        List<AqliManifest> nameMatches = all.stream()
                .filter(manifest -> manifest.getName() != null
                        && requested.equalsIgnoreCase(manifest.getName()))
                .toList();
        if (nameMatches.isEmpty()) {
            return Optional.empty();
        }

        long distinctModels = nameMatches.stream()
                .map(LocalModelCatalog::modelId)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        if (distinctModels > 1) {
            throw new SdkException("SDK_ERR_MODEL_AMBIGUOUS",
                    "Model name '" + requested + "' matches multiple local models.");
        }
        return Optional.of(toModelInfo(newestRevision(nameMatches)));
    }

    private static AqliManifest requireUnambiguousPrefix(String prefix, List<AqliManifest> matches)
            throws SdkException {
        if (matches.size() > 1) {
            throw new SdkException("SDK_ERR_MODEL_AMBIGUOUS",
                    "Model identifier prefix '" + prefix + "' matches multiple local model revisions.");
        }
        return matches.get(0);
    }

    private static AqliManifest newestRevision(List<AqliManifest> revisions) {
        return revisions.stream().min(NEWEST_REVISION_FIRST).orElseThrow();
    }

    private static ModelInfo toModelInfo(AqliManifest manifest) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (manifest.getMetadata() != null) {
            metadata.putAll(manifest.getMetadata());
        }
        putIfPresent(metadata, "manifestId", manifest.getId());
        putIfPresent(metadata, "blobPath", manifest.getBlobPath());
        putIfPresent(metadata, "source", manifest.getSource());
        putIfPresent(metadata, "repository", manifest.getRepository());
        putIfPresent(metadata, "branch", manifest.getBranch());
        putIfPresent(metadata, "taskType", manifest.getTaskType());
        putIfPresent(metadata, "group", manifest.getGroup());
        metadata.put("pipeline", manifest.isPipeline());
        metadata.put("enabled", manifest.isEnabled());
        if (manifest.getFiles() != null && !manifest.getFiles().isEmpty()) {
            metadata.put("files", List.copyOf(manifest.getFiles()));
        }

        String modelId = manifest.getModelId();
        String name = manifest.getName() == null || manifest.getName().isBlank()
                ? modelId : manifest.getName();
        long sizeBytes = manifest.getSizeBytes();

        return ModelInfo.builder()
                .modelId(modelId)
                .shortId(manifest.getShortId())
                .name(name)
                .version(manifest.getRevision())
                .architecture(blankToNull(manifest.getArchitecture()))
                .format(blankToNull(manifest.getFormat()))
                .sizeBytes(sizeBytes > 0 ? sizeBytes : null)
                .createdAt(manifest.getCreatedAt())
                // AqliManifest has no updatedAt field; do not fabricate one.
                .metadata(metadata)
                .build();
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean hasIdentity(AqliManifest manifest) {
        return manifest != null && manifest.getId() != null && !manifest.getId().isBlank()
                && manifest.getModelId() != null && !manifest.getModelId().isBlank();
    }

    private static String modelId(AqliManifest manifest) {
        return manifest.getModelId() == null ? "" : manifest.getModelId();
    }

    private static String revision(AqliManifest manifest) {
        return manifest.getRevision() == null ? "" : manifest.getRevision();
    }

    private static String manifestId(AqliManifest manifest) {
        return manifest.getId() == null ? "" : manifest.getId();
    }
}

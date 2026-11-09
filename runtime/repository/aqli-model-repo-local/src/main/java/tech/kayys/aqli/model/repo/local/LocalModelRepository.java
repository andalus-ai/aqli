package tech.kayys.aqli.model.repo.local;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import tech.kayys.aljabr.core.model.ModelFormat;
import tech.kayys.aljabr.spi.model.ArtifactLocation;
import tech.kayys.aljabr.spi.model.ModelArtifact;
import tech.kayys.aljabr.spi.model.ModelDescriptor;
import tech.kayys.aljabr.spi.model.ModelManifest;
import tech.kayys.aljabr.spi.model.ModelRef;
import tech.kayys.aljabr.spi.model.ModelRepository;
import tech.kayys.aljabr.spi.model.Pageable;

/**
 * Machine-local model repository backed by the durable {@link ManifestStore}.
 *
 * <p>The repository owns manifest metadata only. Deleting a model removes its
 * manifest record but deliberately does not delete model weights; artifact
 * garbage collection requires a separate ownership-aware policy.</p>
 */
@ApplicationScoped
public class LocalModelRepository implements ModelRepository {

    private static final String COMMUNITY = "community";
    private final ManifestStore manifests;

    @Inject
    public LocalModelRepository(ManifestStore manifests) {
        this.manifests = Objects.requireNonNull(manifests, "manifests");
    }

    @Override
    public Uni<ModelManifest> findById(String modelId, String requestId) {
        if (modelId == null || modelId.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(() -> manifests.listAll().stream()
                .filter(manifest -> modelId.equalsIgnoreCase(manifest.getModelId())
                        || modelId.equalsIgnoreCase(manifest.getId())
                        || modelId.equalsIgnoreCase(manifest.getName()))
                .findFirst()
                .map(manifest -> toModelManifest(manifest, requestId))
                .orElse(null));
    }

    @Override
    public Uni<List<ModelManifest>> list(String requestId, Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable");
        if (pageable.page() < 0 || pageable.size() < 1) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Page must be >= 0 and size must be > 0"));
        }
        return Uni.createFrom().item(() -> {
            List<ModelManifest> all = manifests.listAll().stream()
                    .map(manifest -> toModelManifest(manifest, requestId))
                    .toList();
            long offset = (long) pageable.page() * pageable.size();
            if (offset >= all.size()) {
                return List.of();
            }
            return all.subList((int) offset, (int) Math.min(all.size(), offset + pageable.size()));
        });
    }

    @Override
    public Uni<ModelManifest> save(ModelManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        return Uni.createFrom().item(() -> {
            AqliManifest stored = new AqliManifest();
            String revision = manifest.version() == null || manifest.version().isBlank()
                    ? "main" : manifest.version();
            stored.setId(AqliManifest.computeName(manifest.modelId(), revision));
            stored.setModelId(manifest.modelId());
            stored.setName(manifest.name());
            stored.setRevision(revision);
            stored.setBlobPath(resolvePrimaryPath(manifest));
            stored.setFormat(resolveFormat(manifest).getId());
            stored.setCreatedAt(manifest.createdAt());
            stored.setFiles(resolveFiles(stored.getBlobPath()));
            stored.setSizeBytes(resolveSize(stored.getBlobPath()));
            stored.setArchitecture(manifest.architecture());
            Map<String, String> metadata = new LinkedHashMap<>();
            manifest.metadata().forEach((key, value) -> metadata.put(key, String.valueOf(value)));
            stored.setMetadata(metadata);
            stored.setSource("local");
            manifests.save(stored);
            return manifest;
        });
    }

    @Override
    public Uni<Void> delete(String modelId, String requestId) {
        if (modelId == null || modelId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Model ID is required"));
        }
        return Uni.createFrom().item(() -> {
            List<String> ids = manifests.listAll().stream()
                    .filter(manifest -> modelId.equalsIgnoreCase(manifest.getModelId())
                            || modelId.equalsIgnoreCase(manifest.getId())
                            || modelId.equalsIgnoreCase(manifest.getName()))
                    .map(AqliManifest::getId)
                    .toList();
            ids.forEach(manifests::delete);
            return null;
        });
    }

    @Override
    public ModelDescriptor resolve(ModelRef ref) {
        if (!supports(ref)) {
            throw new IllegalArgumentException("Unsupported local model reference: " + ref);
        }
        String configuredPath = ref.parameters() == null ? null : ref.parameters().get("path");
        String candidate = configuredPath != null && !configuredPath.isBlank()
                ? configuredPath
                : joinReference(ref);
        Path path = Path.of(candidate).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Local model path does not exist: " + path);
        }
        String format = ManifestStore.detectFormat(path);
        return new ModelDescriptor(
                path.toString(),
                format,
                path.toUri(),
                Map.of("provider", "local", "path", path.toString()));
    }

    @Override
    public ModelArtifact fetch(ModelDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        Path path = descriptor.source() != null && "file".equalsIgnoreCase(descriptor.source().getScheme())
                ? Path.of(descriptor.source())
                : Path.of(descriptor.id());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Local model artifact does not exist: " + path);
        }
        return new ModelArtifact(path, null, Map.of("provider", "local", "format", descriptor.format()));
    }

    @Override
    public boolean supports(ModelRef ref) {
        return ref != null && ("local".equalsIgnoreCase(ref.scheme())
                || "file".equalsIgnoreCase(ref.scheme()));
    }

    @Override
    public Path downloadArtifact(ModelManifest manifest, ModelFormat format) {
        if (manifest == null || format == null) {
            return null;
        }
        ArtifactLocation artifact = manifest.artifacts().get(format);
        if (artifact != null) {
            Path path = pathFromLocation(artifact.uri());
            return path != null && Files.exists(path) ? path : null;
        }
        Path path = manifest.path() == null ? null : Path.of(manifest.path());
        return path != null && Files.exists(path) && format.matches(path.getFileName().toString()) ? path : null;
    }

    @Override
    public boolean isCached(String modelId, ModelFormat format) {
        if (modelId == null || format == null) {
            return false;
        }
        return manifests.listAll().stream()
                .filter(manifest -> modelId.equalsIgnoreCase(manifest.getModelId()))
                .filter(manifest -> format.getId().equalsIgnoreCase(manifest.getFormat())
                        || format.name().equalsIgnoreCase(manifest.getFormat()))
                .map(AqliManifest::getBlobPath)
                .map(LocalModelRepository::pathFromLocation)
                .anyMatch(path -> path != null && Files.exists(path));
    }

    @Override
    public void evictCache(String modelId, ModelFormat format) {
        // Local model artifacts are user-owned files; this repository never evicts them.
    }

    private ModelManifest toModelManifest(AqliManifest stored, String requestId) {
        Path path = pathFromLocation(stored.getBlobPath());
        Map<ModelFormat, ArtifactLocation> artifacts = new LinkedHashMap<>();
        ModelFormat format = parseFormat(stored.getFormat());
        if (format != ModelFormat.UNKNOWN && stored.getBlobPath() != null) {
            long size = stored.getSizeBytes();
            artifacts.put(format, new ArtifactLocation(
                    path != null ? path.toUri().toString() : stored.getBlobPath(),
                    null,
                    size > 0 ? size : null,
                    "application/octet-stream"));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (stored.getMetadata() != null) {
            metadata.putAll(stored.getMetadata());
        }
        metadata.put("source", stored.getSource() == null ? "local" : stored.getSource());
        metadata.put("revision", stored.getRevision() == null ? "main" : stored.getRevision());
        metadata.put("files", stored.getFiles() == null ? List.of() : List.copyOf(stored.getFiles()));
        return ModelManifest.builder()
                .modelId(stored.getModelId())
                .name(stored.getName() == null ? stored.getModelId() : stored.getName())
                .version(stored.getRevision() == null ? "main" : stored.getRevision())
                .path(stored.getBlobPath() == null ? "" : stored.getBlobPath())
                .apiKey(COMMUNITY)
                .requestId(requestId == null || requestId.isBlank() ? COMMUNITY : requestId)
                .architecture(stored.getArchitecture())
                .artifacts(artifacts)
                .metadata(metadata)
                .createdAt(stored.getCreatedAt())
                .updatedAt(stored.getCreatedAt())
                .build();
    }

    private static String joinReference(ModelRef ref) {
        String namespace = ref.namespace() == null ? "" : ref.namespace().trim();
        String name = ref.name() == null ? "" : ref.name().trim();
        if (name.isBlank()) {
            return namespace;
        }
        if (namespace.isBlank()) {
            return name;
        }
        return Path.of(namespace, name).toString();
    }

    private static String resolvePrimaryPath(ModelManifest manifest) {
        if (manifest.artifacts() != null && !manifest.artifacts().isEmpty()) {
            return manifest.artifacts().values().stream()
                    .map(ArtifactLocation::uri)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(manifest.path());
        }
        return manifest.path();
    }

    private static ModelFormat resolveFormat(ModelManifest manifest) {
        if (manifest.artifacts() != null && !manifest.artifacts().isEmpty()) {
            return manifest.artifacts().keySet().iterator().next();
        }
        String file = manifest.path() == null ? "" : Path.of(manifest.path()).getFileName().toString();
        ModelFormat detected = ModelFormat.fromExtension(extension(file));
        return detected == ModelFormat.UNKNOWN ? ModelFormat.fromId("unknown") : detected;
    }

    private static List<String> resolveFiles(String location) {
        Path path = pathFromLocation(location);
        if (path == null || !Files.exists(path)) {
            return List.of();
        }
        try {
            return ManifestStore.listBlobFiles(path);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to list local model files: " + path, e);
        }
    }

    private static long resolveSize(String location) {
        Path path = pathFromLocation(location);
        if (path == null || !Files.exists(path)) {
            return 0L;
        }
        try {
            if (Files.isRegularFile(path)) {
                return Files.size(path);
            }
            try (var files = Files.walk(path)) {
                return files.filter(Files::isRegularFile).mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }).sum();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to calculate local model size: " + path, e);
        }
    }

    private static ModelFormat parseFormat(String format) {
        if (format == null || format.isBlank()) {
            return ModelFormat.UNKNOWN;
        }
        ModelFormat byId = ModelFormat.fromId(format.toLowerCase(Locale.ROOT));
        if (byId != ModelFormat.UNKNOWN) {
            return byId;
        }
        try {
            return ModelFormat.valueOf(format.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ModelFormat.UNKNOWN;
        }
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }

    private static Path pathFromLocation(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(location);
            return "file".equalsIgnoreCase(uri.getScheme()) ? Path.of(uri) : Path.of(location);
        } catch (RuntimeException ignored) {
            return Path.of(location);
        }
    }
}

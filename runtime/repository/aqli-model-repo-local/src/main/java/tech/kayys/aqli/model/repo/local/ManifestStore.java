package tech.kayys.aqli.model.repo.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Durable JSON-backed index for locally cached model manifests.
 *
 * <p>Writes use a temporary file and an atomic move where supported so a
 * process interruption cannot leave a partially written manifest.</p>
 */
@ApplicationScoped
public class ManifestStore {
    private final Path manifestDir;
    private final ObjectMapper mapper;

    public ManifestStore() {
        this(defaultManifestDir());
    }

    ManifestStore(Path manifestDir) {
        this.manifestDir = manifestDir;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public synchronized List<AqliManifest> listAll() {
        if (!Files.isDirectory(manifestDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(manifestDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::readQuietly)
                    .filter(Optional::isPresent)
                    .map(Optional::orElseThrow)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public synchronized Optional<AqliManifest> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        Path file = manifestDir.resolve(safeFileName(id) + ".json");
        if (Files.isRegularFile(file)) {
            return readQuietly(file);
        }
        return listAll().stream().filter(m -> id.equalsIgnoreCase(m.getId())).findFirst();
    }

    public synchronized Optional<AqliManifest> findByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return listAll().stream().filter(m -> name.equalsIgnoreCase(m.getName()) || name.equalsIgnoreCase(m.getModelId())).findFirst();
    }

    public synchronized Optional<AqliManifest> findByShortId(String shortId) {
        if (shortId == null || shortId.isBlank()) return Optional.empty();
        return listAll().stream().filter(m -> m.getId() != null && m.getId().toLowerCase().startsWith(shortId.toLowerCase())).findFirst();
    }

    public synchronized void delete(String id) {
        if (id == null || id.isBlank()) return;
        try {
            Path file = manifestDir.resolve(safeFileName(id) + ".json");
            Files.deleteIfExists(file);
        } catch (IOException ignored) {}
    }

    public void migrateIfNeeded() {
        // no-op migration hook
    }

    public synchronized void registerLocal(String repoId, Path targetPath) {
        if (repoId == null || targetPath == null) return;
        String manifestName = AqliManifest.computeName(repoId, "main");
        AqliManifest manifest = new AqliManifest();
        manifest.setId(manifestName);
        manifest.setModelId(repoId);
        manifest.setName(manifestName);
        manifest.setFormat(detectFormat(targetPath));
        manifest.setBlobPath(targetPath.toAbsolutePath().toString());
        try {
            manifest.setFiles(listBlobFiles(targetPath));
        } catch (IOException e) {
            manifest.setFiles(List.of(targetPath.getFileName().toString()));
        }
        manifest.setCreatedAt(java.time.Instant.now());
        try {
            manifest.setSizeBytes(Files.size(targetPath));
        } catch (Exception e) {
            manifest.setSizeBytes(0);
        }
        manifest.setArchitecture(detectArchitecture(manifest));
        save(manifest);
    }

    public static String detectFormat(Path dirPath) {
        if (dirPath == null || !Files.exists(dirPath)) {
            return "unknown";
        }
        try {
            List<String> files = listBlobFiles(dirPath);
            for (String f : files) {
                String lower = f.toLowerCase();
                if (lower.endsWith(".gguf")) return "gguf";
                if (lower.endsWith(".safetensors")) return "safetensors";
                if (lower.endsWith(".onnx")) return "onnx";
                if (lower.endsWith(".tflite") || lower.endsWith(".litert")) return "litert";
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    public static String detectArchitecture(AqliManifest manifest) {
        if (manifest == null) return "unknown";
        String id = (manifest.getModelId() != null ? manifest.getModelId() : "").toLowerCase();
        String name = (manifest.getName() != null ? manifest.getName() : "").toLowerCase();
        List<String> files = manifest.getFiles() != null ? manifest.getFiles() : List.of();
        
        String combined = id + " " + name + " " + String.join(" ", files).toLowerCase();
        if (combined.contains("gemma-4") || combined.contains("gemma4")) return "gemma4";
        if (combined.contains("gemma")) return "gemma";
        if (combined.contains("llama")) return "llama";
        if (combined.contains("qwen")) return "qwen";
        if (combined.contains("mistral")) return "mistral";
        if (combined.contains("phi")) return "phi";
        return "unknown";
    }

    public synchronized Optional<AqliManifest> findByModelId(String modelId, String revision) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        String requestedRevision = revision == null || revision.isBlank() ? "main" : revision;
        if (!Files.isDirectory(manifestDir)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.list(manifestDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::readQuietly)
                    .filter(Optional::isPresent)
                    .map(Optional::orElseThrow)
                    .filter(manifest -> modelId.equals(manifest.getModelId()))
                    .filter(manifest -> requestedRevision.equals(
                            manifest.getRevision() == null || manifest.getRevision().isBlank()
                                    ? "main" : manifest.getRevision()))
                    .findFirst();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to list model manifests in " + manifestDir, e);
        }
    }

    public synchronized void save(AqliManifest manifest) {
        if (manifest == null || manifest.getId() == null || manifest.getId().isBlank()) {
            throw new IllegalArgumentException("Manifest and manifest ID are required");
        }
        try {
            Files.createDirectories(manifestDir);
            Path target = manifestDir.resolve(safeFileName(manifest.getId()) + ".json");
            Path temporary = Files.createTempFile(manifestDir, ".manifest-", ".tmp");
            try {
                mapper.writeValue(temporary.toFile(), manifest);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist model manifest " + manifest.getId(), e);
        }
    }

    public static Path getModelsRoot() {
        return Path.of(System.getProperty("user.home"), ".aqli", "models").toAbsolutePath().normalize();
    }

    public static Path resolveBlobDir(String modelId, String manifestName) {
        if (modelId == null || modelId.isBlank() || manifestName == null || manifestName.isBlank()) {
            throw new IllegalArgumentException("Model ID and manifest name are required");
        }
        Path root = getModelsRoot();
        Path modelPath = root;
        for (String segment : modelId.replace('\\', '/').split("/")) {
            if (!segment.isBlank() && !segment.equals(".") && !segment.equals("..")) {
                modelPath = modelPath.resolve(segment.replaceAll("[^A-Za-z0-9._-]", "_"));
            }
        }
        Path result = modelPath.resolve(safeFileName(manifestName)).normalize();
        if (!result.startsWith(root)) {
            throw new IllegalArgumentException("Resolved model cache path escapes the cache root");
        }
        return result;
    }

    public static List<String> listBlobFiles(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return List.of();
        }
        if (Files.isRegularFile(directory)) {
            return List.of(directory.getFileName().toString());
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<String> files = new ArrayList<>();
            paths.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(path -> files.add(directory.relativize(path).toString().replace('\\', '/')));
            return List.copyOf(files);
        }
    }

    private Optional<AqliManifest> readQuietly(Path path) {
        try {
            return Optional.of(mapper.readValue(path.toFile(), AqliManifest.class));
        } catch (IOException | RuntimeException e) {
            // A malformed record must not make other cached models unavailable.
            return Optional.empty();
        }
    }

    private static Path defaultManifestDir() {
        return Path.of(System.getProperty("user.home"), ".aqli", "manifests");
    }

    private static String safeFileName(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            throw new IllegalArgumentException("Invalid manifest identifier");
        }
        return safe;
    }
}

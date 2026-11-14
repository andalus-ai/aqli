package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.model.repo.local.AqliManifest;
import tech.kayys.aqli.model.repo.local.LocalModelArchitectureDetector;
import tech.kayys.aqli.model.repo.local.LocalModelFileInventory;
import tech.kayys.aqli.model.repo.local.LocalModelQuantizationMetadataReader;
import tech.kayys.aqli.model.repo.local.LocalModelRepositoryIdentity;
import tech.kayys.aqli.model.repo.local.LocalModelTaskClassifier;
import tech.kayys.aqli.model.repo.local.ManifestStore;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tech.kayys.aqli.sdk.util.AqliHome;
import tech.kayys.aqli.cli.util.CLIUtils;
import tech.kayys.aqli.cli.util.QuantSuggestionDetector;

final class LocalModelIndex {

    static final class Entry {
        public String id;
        public String shortId;
        public String name;
        public String architecture;
        public String parameterCount;
        public String format;
        public boolean runnable;
        public long sizeBytes;
        public String path;
        public String updatedAt;
        public String source;

        // ── Quantization metadata ────────────────────────────────────
        /** Quantization strategy (bnb, turbo, awq, gptq, autoround), null if not quantized */
        public String quantStrategy;
        /** Bit width (4, 8, etc.), 0 if not quantized */
        public int quantBits;
        /** Group size for block quantization, 0 if not quantized */
        public int quantGroupSize;
        public String quantSourceModel;
        /** Whether the model is enabled for inference */
        public boolean enabled = true;

        // ── Modality / task grouping ─────────────────────────────────
        /**
         * High-level task category derived from the HuggingFace pipeline_tag.
         * Values: text, vision, tts, stt, ocr, multimodal, embedding, unknown.
         */
        public String taskType;
    }

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final LocalModelArchitectureDetector ARCHITECTURE_DETECTOR = new LocalModelArchitectureDetector();
    private static final LocalModelQuantizationMetadataReader QUANTIZATION_METADATA_READER =
            new LocalModelQuantizationMetadataReader();

    private LocalModelIndex() {
    }

    static synchronized List<Entry> refreshFromDisk() {
        List<Entry> entries = scanDiskEntries();
        normalizeShortIds(entries);
        write(entries);
        return entries;
    }

    static synchronized Optional<Entry> find(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        String needle = LocalModelIndexReferenceMatcher.stripProviderPrefix(ref.trim());
        List<Entry> entries = readOrRefresh();
        Optional<Entry> cached = LocalModelIndexReferenceMatcher.findBestMatch(entries, needle);
        if (cached.isPresent()) {
            return cached;
        }
        List<Entry> refreshed = refreshFromDisk();
        return LocalModelIndexReferenceMatcher.findBestMatch(refreshed, needle);
    }

    static synchronized Optional<Entry> find(String ref, String preferredFormat) {
        if (preferredFormat == null || preferredFormat.isBlank()) {
            return find(ref);
        }
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        String needle = LocalModelIndexReferenceMatcher.stripProviderPrefix(ref.trim());
        List<Entry> entries = readOrRefresh();
        Optional<Entry> cached = LocalModelIndexReferenceMatcher.findBestMatch(entries.stream()
                .filter(e -> LocalModelIndexReferenceMatcher.formatMatches(e, preferredFormat))
                .toList(), needle);
        if (cached.isPresent()) {
            return cached;
        }
        List<Entry> refreshed = refreshFromDisk();
        return LocalModelIndexReferenceMatcher.findBestMatch(refreshed.stream()
                .filter(e -> LocalModelIndexReferenceMatcher.formatMatches(e, preferredFormat))
                .toList(), needle);
    }

    static synchronized List<Entry> entries() {
        return new ArrayList<>(readOrRefresh());
    }

    private static List<Entry> readOrRefresh() {
        Path indexPath = indexPath();
        try {
            if (Files.exists(indexPath)) {
                byte[] bytes = Files.readAllBytes(indexPath);
                if (bytes.length > 0) {
                    Entry[] parsed = JSON.readValue(bytes, Entry[].class);
                    List<Entry> entries = new ArrayList<>(List.of(parsed));
                    normalizeShortIds(entries);
                    return entries;
                }
            }
        } catch (Exception ignored) {
            // fallback to refresh
        }
        return refreshFromDisk();
    }

    private static void write(List<Entry> entries) {
        Path indexPath = indexPath();
        try {
            Files.createDirectories(indexPath.getParent());
            JSON.writeValue(indexPath.toFile(), entries);
        } catch (Exception ignored) {
            // best effort cache/index only
        }
    }

    private static Path indexPath() {
        return AqliHome.path("models", "index.json");
    }

    private static List<Entry> scanDiskEntries() {
        List<Entry> out = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        Path root = AqliHome.path("models");
        if (!Files.isDirectory(root)) {
            return out;
        }
        scanManifestEntries(root.resolve("manifests"), out, seenPaths);
        scanFlat(root.resolve("gguf"), "gguf", true, out, seenPaths);
        scanFlat(root.resolve("libtorchscript"), "libtorchscript", true, out, seenPaths);
        scanFlat(root.resolve("torchscript"), "torchscript", true, out, seenPaths);
        scanFlat(root.resolve("safetensors"), "safetensors", true, out, seenPaths);
        scanFlat(root.resolve("litert"), "litert", true, out, seenPaths);
        scanFlat(root.resolve("onnx"), "onnx", true, out, seenPaths);

        out.sort(Comparator.comparing((Entry e) -> parseInstant(e.updatedAt)).reversed());
        return out;
    }

    private static void scanFlat(Path base, String fallbackFormat, boolean runnable, List<Entry> out, Set<String> seenPaths) {
        Map<String, Entry> discovered = new LinkedHashMap<>();
        for (LocalModelFileInventory.Candidate candidate :
                new LocalModelFileInventory().discover(base, fallbackFormat, runnable)) {
            Path file = candidate.file();
            if (seenPaths.add(normalizePath(file))) {
                Entry entry = toEntry(candidate.base(), file, candidate.fallbackFormat(), candidate.runnable());
                discovered.putIfAbsent(entryKey(entry), entry);
            }
        }
        out.addAll(discovered.values());
    }

    private static void scanManifestEntries(Path manifestsDir, List<Entry> out, Set<String> seenPaths) {
        if (!Files.isDirectory(manifestsDir)) {
            return;
        }
        try {
            ManifestStore.ManifestListing listing = new ManifestStore(manifestsDir).listAllWithDiagnostics();
            for (AqliManifest manifest : listing.manifests()) {
                try {
                    String blobPath = manifest.getBlobPath();
                    if (blobPath == null || blobPath.isBlank()) {
                        continue;
                    }
                    Path resolved = Path.of(blobPath).toAbsolutePath().normalize();
                    if (!Files.exists(resolved) || !seenPaths.add(normalizePath(resolved))) {
                        continue;
                    }

                    Entry e = new Entry();
                    e.id = manifest.getModelId();
                    if (e.id == null || e.id.isBlank()) {
                        e.id = manifest.getId();
                    }
                    e.shortId = normalizeShortId(
                            manifest.getPersistedShortId(),
                            e.id != null ? e.id : resolved.getFileName().toString());
                    e.name = manifest.getName();
                    if (e.name == null || e.name.isBlank()) {
                        e.name = resolved.getFileName().toString();
                    }
                    e.architecture = manifest.getArchitecture();
                    e.format = manifest.getFormat();
                    e.path = resolved.toString();
                    e.source = manifest.getSource();
                    e.updatedAt = manifest.getUpdatedAt();
                    e.runnable = isRunnableFormat(e.format);
                    e.enabled = manifest.isEnabled();

                    long sizeBytes = manifest.getSizeBytes();
                    if (sizeBytes <= 0L) {
                        sizeBytes = computeSize(resolved);
                    }
                    e.sizeBytes = sizeBytes;

                    Map<String, String> metadata = manifest.getMetadata();
                    e.taskType = LocalModelTaskClassifier.classify(
                            manifest.getTaskType(),
                            manifest.isTaskTypeSpecified(),
                            metadata == null ? null : metadata.get("pipeline_tag"),
                            e.id,
                            e.name);

                    if (isUnknownArchitecture(e.architecture)) {
                        detectArchitecture(e, Files.isDirectory(resolved) ? resolved : resolved.getParent());
                    }
                    populateQuantMetadata(e, Files.isDirectory(resolved) ? resolved : resolved.getParent());
                    out.add(e);
                } catch (Exception ignored) {
                    // Keep legacy best-effort behavior: one malformed manifest must not stop the scan.
                }
            }
        } catch (Exception ignored) {
            // Best effort; the local index remains available from flat model directories.
        }
    }

    private static Entry toEntry(Path base, Path file, String fallbackFormat, boolean runnable) {
        Entry e = new Entry();
        Path relative = base.relativize(file);
        e.id = LocalModelRepositoryIdentity.modelId(relative, file);
        e.shortId = normalizeShortId(null, e.id);
        e.name = LocalModelRepositoryIdentity.displayName(relative, file);
        e.format = detectFormat(file, fallbackFormat);
        e.runnable = runnable && !e.format.equalsIgnoreCase("safetensors") && !e.format.equalsIgnoreCase("bin");
        Path listedPath = LocalModelRepositoryIdentity.aggregateRepositoryFile(relative, file) ? file.getParent() : file;
        e.path = listedPath.toAbsolutePath().toString();
        e.source = "local";
        double pc = QuantSuggestionDetector.parseParamCount(e.name);
        e.parameterCount = pc > 0 ? String.format("%.1fB", pc) : null;
        
        try {
            e.sizeBytes = computeSize(listedPath);
            e.updatedAt = latestModified(listedPath).toString();
        } catch (Exception ignored) {
            e.sizeBytes = 0L;
        }

        // Try to detect architecture from config.json
        detectArchitecture(e, file.getParent());

        // Read AQLI's local quantization sidecar metadata, when present.
        populateQuantMetadata(e, file.getParent());
        return e;
    }

    private static void normalizeShortIds(List<Entry> entries) {
        if (entries == null) {
            return;
        }
        for (Entry entry : entries) {
            if (entry == null) {
                continue;
            }
            String basis = entry.id;
            if (basis == null || basis.isBlank()) {
                basis = entry.path;
            }
            if (basis == null || basis.isBlank()) {
                basis = entry.name;
            }
            entry.shortId = normalizeShortId(entry.shortId, basis);
        }
    }

    private static String normalizeShortId(String candidate, String basis) {
        if (isHexShortId(candidate)) {
            return candidate.toLowerCase(Locale.ROOT);
        }
        return CLIUtils.generateShortId(basis);
    }

    private static boolean isHexShortId(String value) {
        return value != null
                && value.length() == 6
                && value.toLowerCase(Locale.ROOT).chars().allMatch(LocalModelIndex::isHexChar);
    }

    private static boolean isHexChar(int ch) {
        return (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }

    private static boolean isRunnableFormat(String format) {
        if (format == null || format.isBlank()) {
            return true;
        }
        String normalized = format.toLowerCase(Locale.ROOT);
        return normalized.equals("gguf")
                || normalized.equals("safetensors")
                || normalized.equals("safetensor")
                || normalized.equals("litert")
                || normalized.equals("onnx")
                || normalized.equals("task")
                || normalized.equals("tflite");
    }

    private static long computeSize(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.size(path);
            }
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    return walk.filter(Files::isRegularFile)
                            .mapToLong(p -> {
                                try {
                                    return Files.size(p);
                                } catch (Exception ignored) {
                                    return 0L;
                                }
                            })
                            .sum();
                }
            }
        } catch (Exception ignored) {
            // best effort
        }
        return 0L;
    }

    private static Instant latestModified(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.getLastModifiedTime(path).toInstant();
            }
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    return walk.filter(Files::isRegularFile)
                            .map(p -> {
                                try {
                                    return Files.getLastModifiedTime(p).toInstant();
                                } catch (Exception ignored) {
                                    return Instant.EPOCH;
                                }
                            })
                            .max(Comparator.naturalOrder())
                            .orElse(Instant.EPOCH);
                }
            }
        } catch (Exception ignored) {
            // best effort
        }
        return Instant.EPOCH;
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node == null || field == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static void detectArchitecture(Entry e, Path modelDir) {
        if (modelDir == null) return;
        if (Files.isRegularFile(modelDir.resolve("tts_browser_onnx_meta.json"))) {
            e.architecture = "moss-tts";
            return;
        }
        if (Files.isRegularFile(modelDir.resolve("codec_browser_onnx_meta.json"))) {
            e.architecture = "moss-audio-tokenizer";
            return;
        }
        ARCHITECTURE_DETECTOR.detect(modelDir).ifPresent(architecture -> e.architecture = architecture);
    }

    private static boolean isUnknownArchitecture(String architecture) {
        return architecture == null
                || architecture.isBlank()
                || "unknown".equalsIgnoreCase(architecture);
    }

    private static void populateQuantMetadata(Entry e, Path modelDir) {
        if (modelDir == null) {
            return;
        }
        QUANTIZATION_METADATA_READER.read(modelDir).ifPresent(metadata -> {
            e.quantStrategy = metadata.strategy();
            e.quantBits = metadata.bits();
            e.quantGroupSize = metadata.groupSize();
            e.quantSourceModel = metadata.sourceModel();
        });
    }

    private static String detectFormat(Path file, String fallback) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".gguf")) {
            return "gguf";
        }
        if (name.endsWith(".safetensors") || name.endsWith(".safetensor")) {
            return "safetensors";
        }
        if (name.endsWith(".onnx")) {
            return "onnx";
        }
        if (name.endsWith(".tflite") || name.endsWith(".litert") || name.endsWith(".litertlm") || name.endsWith(".task")) {
            return "litert";
        }
        if (name.endsWith(".pt") || name.endsWith(".pth")) {
            return "torchscript";
        }
        if (name.endsWith(".bin")) {
            return "bin";
        }
        return fallback;
    }

    static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.EPOCH;
        }
    }
}

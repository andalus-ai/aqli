package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.model.repo.local.AqliManifest;
import tech.kayys.aqli.model.repo.local.LocalModelArchitectureDetector;
import tech.kayys.aqli.model.repo.local.LocalModelFileInventory;
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

                    // Preserve the legacy pipeline-tag fallback when taskType was omitted/defaulted.
                    e.taskType = manifest.getTaskType();
                    if (!manifest.isTaskTypeSpecified() || e.taskType == null || e.taskType.isBlank()) {
                        String tag = manifest.getMetadata() == null
                                ? null : manifest.getMetadata().get("pipeline_tag");
                        String mappedTaskType = pipelineTagToTaskType(tag);
                        if (mappedTaskType != null) {
                            e.taskType = mappedTaskType;
                        }
                    }
                    if (e.taskType == null || e.taskType.isBlank() || "text".equals(e.taskType)) {
                        String lowerId = (e.id != null ? e.id : "").toLowerCase(Locale.ROOT);
                        String lowerName = (e.name != null ? e.name : "").toLowerCase(Locale.ROOT);
                        if (lowerId.contains("ocr") || lowerName.contains("ocr")
                                || lowerId.contains("pix2text") || lowerName.contains("pix2text")
                                || lowerId.contains("trocr") || lowerId.contains("nougat")) {
                            e.taskType = "ocr";
                        } else if (lowerId.contains("3d") || lowerName.contains("3d")
                                || lowerId.contains("block3d") || lowerName.contains("block3d")
                                || lowerId.contains("trellis") || lowerName.contains("trellis")
                                || lowerId.contains("shap-e") || lowerName.contains("shap-e")
                                || lowerId.contains("mesh") || lowerName.contains("mesh")
                                || lowerId.contains("splat") || lowerName.contains("splat")) {
                            e.taskType = "3d";
                        } else if (e.taskType == null || e.taskType.isBlank()) {
                            e.taskType = "text";
                        }
                    }

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
        e.id = deriveModelId(relative, file);
        e.shortId = normalizeShortId(null, e.id);
        e.name = deriveDisplayName(relative, file);
        e.format = detectFormat(file, fallbackFormat);
        e.runnable = runnable && !e.format.equalsIgnoreCase("safetensors") && !e.format.equalsIgnoreCase("bin");
        Path listedPath = shouldAggregateRepositoryFile(relative, file) ? file.getParent() : file;
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

        // Populate quantization metadata from aqli_quant.json if present
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

    private static String deriveModelId(Path relative, Path file) {
        if (relative == null) {
            return "";
        }
        String normalized = relative.toString().replace("\\", "/");
        if (relative.getNameCount() >= 3) {
            String fileName = file.getFileName().toString();
            if (!shouldUseRepositoryDisplayName(fileName)) {
                return relative.getName(0) + "/" + fileName;
            }
            return relative.getName(0) + "/" + relative.getName(1);
        }
        return normalized;
    }

    private static String entryKey(Entry entry) {
        String id = entry.id != null ? entry.id : "";
        String format = entry.format != null ? entry.format : "";
        String path = entry.path != null ? entry.path : "";
        return id + "|" + format + "|" + path;
    }

    private static String deriveDisplayName(Path relative, Path file) {
        if (relative != null && relative.getNameCount() >= 3 && shouldUseRepositoryDisplayName(file.getFileName().toString())) {
            return relative.getName(1).toString();
        }
        String fileName = file.getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if ((lower.equals("model.safetensors")
                || lower.equals("model.safetensor")
                || lower.equals("model.onnx")
                || lower.equals("model.tflite")
                || lower.equals("model.litert")
                || lower.equals("model.litertlm")
                || lower.equals("model.task"))
                && file.getParent() != null
                && file.getParent().getFileName() != null) {
            return file.getParent().getFileName().toString();
        }
        return fileName;
    }

    private static boolean shouldUseRepositoryDisplayName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".onnx")
                || lower.equals("model.safetensors")
                || lower.equals("model.safetensor")
                || lower.equals("model.tflite")
                || lower.equals("model.litert")
                || lower.equals("model.litertlm")
                || lower.equals("model.task");
    }

    private static boolean shouldAggregateRepositoryFile(Path relative, Path file) {
        if (relative == null || relative.getNameCount() < 3 || file == null) {
            return false;
        }
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".onnx");
    }

    /**
     * Maps a HuggingFace {@code pipeline_tag} value to a aqli task-type label.
     * Returns {@code null} when the tag is blank or unrecognised (caller decides default).
     */
    static String pipelineTagToTaskType(String pipelineTag) {
        if (pipelineTag == null || pipelineTag.isBlank()) {
            return null;
        }
        return switch (pipelineTag.trim().toLowerCase(Locale.ROOT)) {
            case "text-generation",
                 "text2text-generation",
                 "conversational",
                 "fill-mask"             -> "text";
            case "automatic-speech-recognition",
                 "audio-classification"  -> "stt";
            case "text-to-audio",
                 "text-to-speech"        -> "tts";
            case "ocr",
                 "optical-character-recognition" -> "ocr";
            case "image-to-text",
                 "document-question-answering",
                 "visual-question-answering",
                 "image-text-to-text"    -> "vision";
            case "image-classification",
                 "object-detection",
                 "image-segmentation",
                 "image-to-image"        -> "vision";
            case "feature-extraction",
                 "sentence-similarity"   -> "embedding";
            case "time-series-forecasting",
                 "time-series-classification",
                 "tabular-regression",
                 "tabular-classification" -> "timeseries";
            case "text-to-3d",
                 "image-to-3d",
                 "3d-generation",
                 "mesh-generation",
                 "3d"                    -> "3d";
            default                      -> null;
        };
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

    @SuppressWarnings("unchecked")
    private static void populateQuantMetadata(Entry e, Path modelDir) {
        if (modelDir == null) return;
        Path quantMeta = modelDir.resolve("aqli_quant.json");
        if (!Files.isRegularFile(quantMeta)) return;
        try {
            var map = JSON.readValue(quantMeta.toFile(), java.util.Map.class);
            var quant = (java.util.Map<String, Object>) map.get("aqli_quant");
            if (quant != null) {
                e.quantStrategy = (String) quant.get("strategy");
                e.quantBits = quant.get("bits") instanceof Number n ? n.intValue() : 0;
                e.quantGroupSize = quant.get("group_size") instanceof Number n ? n.intValue() : 0;
                e.quantSourceModel = (String) quant.get("source_model");
            }
        } catch (Exception ignored) {
            // best effort — skip if malformed
        }
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

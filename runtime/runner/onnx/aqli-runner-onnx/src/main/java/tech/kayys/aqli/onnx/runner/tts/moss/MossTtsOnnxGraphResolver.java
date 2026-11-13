package tech.kayys.aqli.onnx.runner.tts.moss;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tech.kayys.aqli.onnx.runner.tts.moss.codec.MossAudioTokenizerModelLayout;
import tech.kayys.aqli.onnx.runner.tts.moss.model.MossTtsModelLayout;

/**
 * Resolves the ONNX graph set and optimized-cache identity for a local MOSS TTS model.
 */
public final class MossTtsOnnxGraphResolver {
    private MossTtsOnnxGraphResolver() {
    }

    public static List<Graph> resolveGraphs(Path modelDir, Path codecDir) throws Exception {
        JsonNode ttsMeta = new ObjectMapper().readTree(
                modelDir.resolve(MossTtsModelLayout.METADATA_FILE_NAME).toFile());
        JsonNode codecMeta = new ObjectMapper().readTree(
                codecDir.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME).toFile());
        return resolveGraphs(modelDir, codecDir, ttsMeta, codecMeta);
    }

    static List<Graph> resolveGraphs(
            Path modelDir, Path codecDir, JsonNode ttsMeta, JsonNode codecMeta) {
        JsonNode ttsFiles = ttsMeta.path("files");
        JsonNode codecFiles = codecMeta.path("files");
        return List.of(
                new Graph("tts prefill", modelDir.resolve(ttsFiles.path("prefill").asText("moss_tts_prefill.onnx"))),
                new Graph("tts decode", modelDir.resolve(ttsFiles.path("decode_step").asText("moss_tts_decode_step.onnx"))),
                new Graph("local sampler", modelDir.resolve(ttsFiles.path("local_fixed_sampled_frame")
                        .asText("moss_tts_local_fixed_sampled_frame.onnx"))),
                new Graph("codec full decode", codecDir.resolve(codecFiles.path("decode_full")
                        .asText("moss_audio_tokenizer_decode_full.onnx"))),
                new Graph("codec streaming decode", codecDir.resolve(codecFiles.path("decode_step")
                        .asText("moss_audio_tokenizer_decode_step.onnx"))));
    }

    public static Path optimizedCachePath(Path modelPath, Path cacheRoot) throws Exception {
        return optimizedCachePath(modelPath, cacheRoot, externalDataSidecars(modelPath));
    }

    /**
     * Resolves an optimized graph path using a previously discovered sidecar inventory.
     * This lets diagnostics reuse one directory scan for graphs that share a source directory.
     */
    public static Path optimizedCachePath(
            Path modelPath, Path cacheRoot, List<Path> externalDataSidecars) throws Exception {
        String fingerprint = optimizedModelFingerprint(
                modelPath.toAbsolutePath().normalize(), externalDataSidecars);
        String filename = cacheFilenamePrefix(modelPath) + fingerprint.substring(0, 16) + ".optimized.onnx";
        return cacheScopeDirectory(modelPath, cacheRoot).resolve(filename);
    }

    /**
     * Gives each source directory an isolated cache namespace. ONNX external-data
     * locations are relative to the optimized graph, so sidecars with identical
     * filenames from different model directories must not share one cache directory.
     */
    public static Path cacheScopeDirectory(Path modelPath, Path cacheRoot) throws Exception {
        Path sourceDirectory = modelPath.toAbsolutePath().normalize().getParent();
        String identity = sourceDirectory == null
                ? modelPath.toAbsolutePath().normalize().toString()
                : sourceDirectory.toRealPath().toString();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String scope = HexFormat.of().formatHex(digest.digest()).substring(0, 24);
        return cacheRoot.resolve("source-" + scope);
    }

    public static String cacheFilenamePrefix(Path modelPath) {
        return sanitizeCacheFilename(modelPath.getFileName().toString()) + "-";
    }

    /**
     * Prefix for entries in the new source-scoped layout. The plain filename
     * prefix remains available to recognize legacy root-level cache entries.
     */
    public static String cacheEntryPrefix(Path modelPath, Path cacheRoot) throws Exception {
        Path relativeScope = cacheRoot.toAbsolutePath().normalize()
                .relativize(cacheScopeDirectory(modelPath, cacheRoot).toAbsolutePath().normalize());
        return relativeScope.resolve(cacheFilenamePrefix(modelPath)).toString();
    }

    public static List<Path> externalDataSidecars(Path modelPath) throws Exception {
        Path sourceDir = modelPath.toAbsolutePath().normalize().getParent();
        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(sourceDir)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".data"))
                    .sorted((left, right) -> left.getFileName().toString()
                            .compareTo(right.getFileName().toString()))
                    .toList();
        }
    }

    public static void updateGraphIdentityDigest(MessageDigest digest, Path modelPath) throws Exception {
        updateGraphIdentityDigest(digest, modelPath, externalDataSidecars(modelPath));
    }

    private static void updateGraphIdentityDigest(
            MessageDigest digest, Path modelPath, List<Path> sidecars) throws Exception {
        updateFileIdentityDigest(digest, "onnx", modelPath);
        updateDigestLine(digest, "external_data_sidecars=" + sidecars.size());
        for (Path sidecar : sidecars) {
            updateFileIdentityDigest(digest, "data", sidecar);
        }
    }

    private static String optimizedModelFingerprint(Path modelPath, List<Path> sidecars) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        updateDigestLine(digest, "os=" + System.getProperty("os.name", ""));
        updateDigestLine(digest, "arch=" + System.getProperty("os.arch", ""));
        updateDigestLine(digest, "runtime=ort-java");
        updateDigestLine(digest, "optimization=all-opt");
        updateDigestLine(digest, "backend=cpu");
        updateGraphIdentityDigest(digest, modelPath, sidecars);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateFileIdentityDigest(MessageDigest digest, String kind, Path path) throws Exception {
        Path normalized = path.toAbsolutePath().normalize();
        updateDigestLine(digest, String.join("\t",
                kind,
                normalized.toRealPath().toString(),
                String.valueOf(Files.size(normalized)),
                String.valueOf(Files.getLastModifiedTime(normalized).toMillis())));
    }

    private static void updateDigestLine(MessageDigest digest, String text) {
        digest.update(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static String sanitizeCacheFilename(String value) {
        String normalized = value == null ? "model" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("[-_.]{2,}", "-")
                .replaceAll("^[-_.]+|[-_.]+$", "");
        return normalized.isBlank() ? "model" : normalized;
    }

    public record Graph(String label, Path path) {
    }
}

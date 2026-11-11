package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Resolves the ONNX graph set and optimized-cache identity for a local MOSS TTS model.
 */
public final class MossTtsOnnxGraphResolver {
    private static final String CODEC_META_FILE = "codec_browser_onnx_meta.json";

    private MossTtsOnnxGraphResolver() {
    }

    public static List<Graph> resolveGraphs(Path modelDir, Path codecDir) throws Exception {
        JsonNode ttsMeta = new ObjectMapper().readTree(modelDir.resolve("tts_browser_onnx_meta.json").toFile());
        JsonNode codecMeta = new ObjectMapper().readTree(codecDir.resolve(CODEC_META_FILE).toFile());
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
        String fingerprint = optimizedModelFingerprint(modelPath.toAbsolutePath().normalize());
        String filename = cacheFilenamePrefix(modelPath) + fingerprint.substring(0, 16) + ".optimized.onnx";
        return cacheRoot.resolve(filename);
    }

    public static String cacheFilenamePrefix(Path modelPath) {
        return sanitizeCacheFilename(modelPath.getFileName().toString()) + "-";
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

    private static String optimizedModelFingerprint(Path modelPath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        updateDigestLine(digest, "os=" + System.getProperty("os.name", ""));
        updateDigestLine(digest, "arch=" + System.getProperty("os.arch", ""));
        updateDigestLine(digest, "runtime=ort-java");
        updateDigestLine(digest, "optimization=all-opt");
        updateDigestLine(digest, "backend=cpu");
        updateFileIdentityDigest(digest, "onnx", modelPath);
        List<Path> sidecars = externalDataSidecars(modelPath);
        updateDigestLine(digest, "external_data_sidecars=" + sidecars.size());
        for (Path sidecar : sidecars) {
            updateFileIdentityDigest(digest, "data", sidecar);
        }
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

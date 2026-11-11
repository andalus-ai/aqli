package tech.kayys.aqli.onnx.runner.tts.moss.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Resolves and validates the on-disk layout of a local MOSS TTS ONNX model. */
public final class MossTtsModelLayout {
    private MossTtsModelLayout() {
    }

    public static Optional<Path> resolveDirectory(String finalLocalPath, String modelId) {
        for (String candidate : new String[] { finalLocalPath, modelId }) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Path path;
            try {
                path = Path.of(candidate);
            } catch (RuntimeException ignored) {
                continue;
            }
            Optional<Path> resolved = directoryFrom(path);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    public static Optional<Path> directoryFrom(Path path) {
        if (path == null) {
            return Optional.empty();
        }
        if (isModelDirectory(path)) {
            return Optional.of(path);
        }
        Path parent = path.getParent();
        if (parent != null && isModelDirectory(parent)) {
            return Optional.of(parent);
        }
        return Optional.empty();
    }

    public static boolean isModelDirectory(Path path) {
        return path != null
                && Files.isRegularFile(path.resolve("browser_poc_manifest.json"))
                && Files.isRegularFile(path.resolve("tts_browser_onnx_meta.json"));
    }
}

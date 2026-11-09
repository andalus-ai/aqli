package tech.kayys.aqli.cli.commands;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves a local ONNX model directory or graph from CLI model references.
 *
 * <p>Direct local paths take precedence over the local model index. The
 * supplied path function preserves the CLI's path expansion policy without
 * making this resolver responsible for shell-specific path handling.</p>
 */
final class OnnxDiagnosticModelPathResolver {

    private OnnxDiagnosticModelPathResolver() {
    }

    static Path resolve(String finalLocalPath, String modelId, Function<String, Path> existingPath) {
        for (String candidate : new String[] {finalLocalPath, modelId}) {
            Path direct = existingPath.apply(candidate);
            if (direct != null) {
                return direct;
            }
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                Optional<LocalModelIndex.Entry> indexed = LocalModelIndex.find(candidate, "onnx");
                if (indexed.isEmpty()) {
                    indexed = LocalModelIndex.find(candidate);
                }
                if (indexed.isPresent() && indexed.get().path != null) {
                    Path indexedPath = existingPath.apply(indexed.get().path);
                    if (indexedPath != null) {
                        return indexedPath;
                    }
                }
            } catch (Exception ignored) {
                // Continue with the next candidate.
            }
        }
        return null;
    }
}

package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import tech.kayys.aqli.model.repo.local.ManifestStore;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;

/**
 * Resolves explicit local model inputs supplied to the run command.
 *
 * <p>This adapter handles only {@code --modelFile}, {@code --modelDir}, and
 * {@code --model-path}. Repository lookup, downloads, route selection, and
 * runner compatibility remain owned by their existing runtime/CLI policies.</p>
 */
final class ExplicitModelInputResolver {

    record Options(
            String modelFile,
            String modelDir,
            String modelPath,
            boolean importModel,
            boolean copyModel,
            boolean providerExplicit,
            String providerId,
            String format) {
    }

    record Result(
            String modelId,
            String localPath,
            boolean customModelPathUsed,
            String providerId,
            String format,
            String error,
            List<String> messages) {

        Result {
            messages = List.copyOf(messages);
        }

        boolean successful() {
            return error == null;
        }

        boolean resolved() {
            return customModelPathUsed;
        }
    }

    private ExplicitModelInputResolver() {
    }

    static Result resolve(Options options, AqliRuntime runtime) {
        List<String> messages = new ArrayList<>();
        if (options == null) {
            throw new IllegalArgumentException("explicit model input options are required");
        }

        String providerId = options.providerId();
        String format = options.format();

        if (options.modelFile() != null && !options.modelFile().isBlank()) {
            Path filePath = Paths.get(options.modelFile());
            if (!Files.exists(filePath)) {
                return failure(providerId, format, "Error: Model file not found: " + options.modelFile());
            }

            if (options.importModel() || options.copyModel()) {
                try {
                    var imported = runtime.importModel(filePath, options.importModel());
                    filePath = Paths.get(imported.getLocalPath());
                    messages.add((options.importModel() ? "Imported" : "Copied")
                            + " model to: " + filePath.toAbsolutePath());
                } catch (Exception e) {
                    return failure(providerId, format, "Failed to import model: " + e.getMessage());
                }
            }

            String modelId = filePath.toAbsolutePath().toString();
            String inferredFormat = ModelFormatResolver.formatForPath(filePath);
            if ((format == null || format.isBlank()) && inferredFormat != null) {
                format = inferredFormat;
            }
            if (!options.providerExplicit() && (providerId == null || providerId.isBlank())) {
                providerId = ModelFormatResolver.providerForFormat(inferredFormat);
            }
            if ("native".equals(providerId)) {
                String suppliedPath = options.modelFile();
                if (suppliedPath.endsWith(".litertlm")
                        || suppliedPath.endsWith(".tflite")
                        || suppliedPath.endsWith(".task")) {
                    providerId = "litert";
                }
            }
            messages.add("Model path: " + filePath.toAbsolutePath());
            return resolved(modelId, providerId, format, messages);
        }

        if (options.modelDir() != null && !options.modelDir().isBlank()) {
            Path dirPath = Paths.get(options.modelDir());
            if (!Files.isDirectory(dirPath)) {
                return failure(providerId, format, "Error: Model directory not found: " + options.modelDir());
            }

            if (options.importModel() || options.copyModel()) {
                try {
                    var imported = runtime.importModel(dirPath, options.importModel());
                    dirPath = Paths.get(imported.getLocalPath());
                    messages.add((options.importModel() ? "Imported" : "Copied")
                            + " model to: " + dirPath.toAbsolutePath());
                } catch (Exception e) {
                    return failure(providerId, format, "Failed to import model: " + e.getMessage());
                }
            }

            String modelId = dirPath.toAbsolutePath().toString();
            String detectedFormat = formatForModelDirectory(dirPath);
            if (!options.providerExplicit()) {
                providerId = providerForModelDirectory(detectedFormat);
            }
            if (format == null || format.isBlank()) {
                format = detectedFormat;
            }
            messages.add("Model dir: " + dirPath.toAbsolutePath());
            return resolved(modelId, providerId, format, messages);
        }

        if (options.modelPath() != null && !options.modelPath().isEmpty()) {
            Path customModelPath = Paths.get(options.modelPath());
            if (!Files.exists(customModelPath)) {
                return failure(providerId, format, "Error: Model file not found: " + options.modelPath());
            }
            messages.add("Using model from: " + customModelPath.toAbsolutePath());
            String modelId = customModelPath.toAbsolutePath().toString();
            String inferredFormat = ModelFormatResolver.formatForPath(customModelPath);
            if ((format == null || format.isBlank()) && inferredFormat != null) {
                format = inferredFormat;
            }
            if (!options.providerExplicit() && (providerId == null || providerId.isBlank())) {
                providerId = ModelFormatResolver.providerForFormat(inferredFormat);
            }
            return resolved(modelId, providerId, format, messages);
        }

        return new Result(null, null, false, providerId, format, null, List.of());
    }

    private static String formatForModelDirectory(Path directory) {
        String detected = ManifestStore.detectFormat(directory);
        if (detected == null || detected.isBlank() || "unknown".equalsIgnoreCase(detected)) {
            return null;
        }
        return ModelFormatResolver.canonicalFormat(detected);
    }

    private static String providerForModelDirectory(String detectedFormat) {
        String provider = ModelFormatResolver.providerForFormat(detectedFormat);
        return provider != null ? provider : "safetensor";
    }

    private static Result resolved(String modelId, String providerId, String format, List<String> messages) {
        return new Result(modelId, modelId, true, providerId, format, null, messages);
    }

    private static Result failure(String providerId, String format, String error) {
        return new Result(null, null, false, providerId, format, error, List.of());
    }
}

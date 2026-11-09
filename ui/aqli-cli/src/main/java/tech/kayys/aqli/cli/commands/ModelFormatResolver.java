package tech.kayys.aqli.cli.commands;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves format names and lightweight format hints used during CLI model routing.
 *
 * <p>This class does not inspect model contents or select an execution runner.
 * It only normalizes names and recognizes unambiguous path/reference hints.</p>
 */
final class ModelFormatResolver {

    private ModelFormatResolver() {
    }

    static String canonicalFormat(String requestedFormat) {
        if (requestedFormat == null || requestedFormat.isBlank()) {
            return null;
        }
        return switch (requestedFormat.trim().toLowerCase(Locale.ROOT)) {
            case "onnx" -> "onnx";
            case "safetensor", "safetensors" -> "safetensors";
            case "litert", "tflite", "task", "litertlm" -> "litert";
            case "gguf" -> "gguf";
            case "torchscript", "pytorch", "pt", "pth", "pts" -> "torchscript";
            default -> requestedFormat.trim().toLowerCase(Locale.ROOT);
        };
    }

    static String formatForPath(Path modelPath) {
        if (modelPath == null || modelPath.getFileName() == null) {
            return null;
        }
        String name = modelPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".gguf")) {
            return "gguf";
        }
        if (name.endsWith(".litertlm") || name.endsWith(".tflite") || name.endsWith(".task")) {
            return "litert";
        }
        if (name.endsWith(".onnx")) {
            return "onnx";
        }
        if (name.endsWith(".safetensors") || name.endsWith(".safetensor")) {
            return "safetensors";
        }
        if (name.endsWith(".pt") || name.endsWith(".pth") || name.endsWith(".pts")) {
            return "torchscript";
        }
        return null;
    }

    static boolean looksLikeOnnxModelReference(String requestedModel) {
        if (requestedModel == null) {
            return false;
        }
        String normalized = requestedModel.trim();
        if (normalized.regionMatches(true, 0, "hf:", 0, 3)) {
            normalized = normalized.substring(3);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return normalized.startsWith("onnx-community/")
                || normalized.endsWith("-onnx")
                || normalized.endsWith("_onnx")
                || normalized.contains("/onnx")
                || normalized.contains("-onnx-")
                || normalized.contains("_onnx_");
    }

    static String providerForFormat(String format) {
        if (format == null) {
            return null;
        }
        return switch (format.trim().toLowerCase(Locale.ROOT)) {
            case "gguf" -> "gguf";
            case "safetensors", "safetensor" -> "safetensor";
            case "litert", "task", "tflite", "litertlm" -> "litert";
            case "onnx" -> "onnx";
            case "torchscript", "pytorch", "pt", "pth", "pts" -> "libtorch";
            default -> null;
        };
    }

    static String normalizeProvider(String rawProviderId) {
        if (rawProviderId == null) {
            return null;
        }
        String trimmed = rawProviderId.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "torch", "torchscript", "pytorch" -> "libtorch";
            default -> trimmed.toLowerCase(Locale.ROOT);
        };
    }

    static boolean isGgufFormat(String value) {
        return value != null && value.trim().equalsIgnoreCase("gguf");
    }

    static boolean isOnnxProvider(String value) {
        return value != null && "onnx".equalsIgnoreCase(value.trim());
    }
}

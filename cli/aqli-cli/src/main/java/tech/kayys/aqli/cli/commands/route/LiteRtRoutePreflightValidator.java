package tech.kayys.aqli.cli.commands.route;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Detects LiteRT artifacts whose execution route is intentionally disabled
 * pending a correct and performant native runner.
 *
 * <p>This is a local-artifact preflight guard; it does not select a provider
 * or execute a model.</p>
 */
public final class LiteRtRoutePreflightValidator {

    private static final String ENABLE_EXPERIMENTAL_TASK_RUNNER =
            "aqli.litert.enable_experimental_gemma4_task_runner";
    private static final String ENABLE_EXPERIMENTAL_RAW_LITERTLM =
            "aqli.litert.enable_experimental_raw_litertlm";

    private LiteRtRoutePreflightValidator() {
    }

    /**
     * Returns a diagnostic when a Gemma 4 LiteRT artifact has no supported
     * native LiteRT-LM companion, or {@code null} when this guard does not apply.
     */
    public static String detectUnsupported(String provider, String localPath) {
        if (!"litert".equalsIgnoreCase(provider) || localPath == null || localPath.isBlank()) {
            return null;
        }
        if (Boolean.getBoolean(ENABLE_EXPERIMENTAL_TASK_RUNNER)
                || Boolean.getBoolean(ENABLE_EXPERIMENTAL_RAW_LITERTLM)) {
            return null;
        }
        try {
            Path path = Path.of(localPath);
            if (!Files.exists(path) || !looksLikeGemma4LiteRtArtifact(path)) {
                return null;
            }
            if (hasNativeGemmaLiteRtLm(path)) {
                return null;
            }
            return "Gemma 4 LiteRT runner is disabled by default because the current Java/Metal paths are slow "
                    + "and produce incorrect repeated-token output for this export. Set -D"
                    + ENABLE_EXPERIMENTAL_TASK_RUNNER + "=true only for diagnostics.";
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean looksLikeGemma4LiteRtArtifact(Path path) {
        String lower = path.toString().toLowerCase(Locale.ROOT);
        if (Files.isRegularFile(path)) {
            if (!lower.contains("gemma-4") && !lower.contains("gemma4")) {
                return false;
            }
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return isLiteRtModelFileName(name);
        }
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (var stream = Files.list(path)) {
            return stream.anyMatch(candidate -> {
                String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
                return (name.contains("gemma-4") || name.contains("gemma4"))
                        && isLiteRtModelFileName(name);
            });
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasNativeGemmaLiteRtLm(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".litertlm")
                        && (name.contains("gemma-4") || name.contains("gemma4"));
            }
            if (!Files.isDirectory(path)) {
                return false;
            }
            try (var stream = Files.list(path)) {
                return stream.anyMatch(candidate -> {
                    String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
                    return Files.isRegularFile(candidate)
                            && name.endsWith(".litertlm")
                            && (name.contains("gemma-4") || name.contains("gemma4"))
                            && !name.contains("qualcomm");
                });
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isLiteRtModelFileName(String name) {
        return name.endsWith(".litertlm")
                || name.endsWith(".task")
                || name.endsWith(".tflite")
                || name.endsWith(".tfl");
    }
}

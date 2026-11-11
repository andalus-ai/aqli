package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tech.kayys.aqli.sdk.util.AqliHome;

/**
 * Lightweight auto-selector for fast daemon prewarm targets.
 */
public final class PrewarmAutoPlan {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 2;
    private static final String DEFAULT_FORMATS = "litert,gguf";

    private PrewarmAutoPlan() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 && !args[0].isBlank() ? args[0] : "auto";
        int limit = intSetting("aqli.prewarm.auto.limit",
                "AQLI_PREWARM_AUTO_LIMIT",
                "AQLI_INSTALL_PREWARM_AUTO_LIMIT",
                DEFAULT_LIMIT);
        String formats = stringSetting("aqli.prewarm.auto.formats",
                "AQLI_PREWARM_AUTO_FORMATS",
                "AQLI_INSTALL_PREWARM_AUTO_FORMATS",
                DEFAULT_FORMATS);
        for (String ref : select(AqliHome.path("models", "index.json"), mode, limit, formats)) {
            System.out.println(ref);
        }
    }

    static List<String> select(Path indexPath, String mode, int limit, String formats) {
        if (indexPath == null || !Files.isRegularFile(indexPath)) {
            return List.of();
        }
        List<String> order = formatOrder(mode, formats);
        if (order.isEmpty()) {
            return List.of();
        }
        int cappedLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        try {
            LocalModelIndex.Entry[] entries = JSON.readValue(indexPath.toFile(), LocalModelIndex.Entry[].class);
            Map<String, LocalModelIndex.Entry> first = new LinkedHashMap<>();
            Map<String, LocalModelIndex.Entry> best = new LinkedHashMap<>();
            Set<String> wanted = new LinkedHashSet<>(order);
            for (LocalModelIndex.Entry entry : entries) {
                String key = fastFormatKey(entry);
                if (key == null || !wanted.contains(key) || !entry.runnable || !hasUsableFastPath(entry, key)) {
                    continue;
                }
                first.putIfAbsent(key, entry);
                if (isPreferred(entry)) {
                    best.putIfAbsent(key, entry);
                }
            }
            List<String> out = new ArrayList<>();
            Set<String> emitted = new LinkedHashSet<>();
            for (String key : order) {
                if (out.size() >= cappedLimit) {
                    break;
                }
                LocalModelIndex.Entry selected = best.getOrDefault(key, first.get(key));
                String ref = modelRef(selected);
                if (ref != null && emitted.add(ref)) {
                    out.add(ref);
                }
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<String> formatOrder(String mode, String formats) {
        String normalizedMode = normalize(mode);
        String resolvedFormats = switch (normalizedMode) {
            case "auto:gguf", "gguf:auto" -> "gguf";
            case "auto:litert", "litert:auto", "auto:litertlm", "litertlm:auto" -> "litert";
            case "auto", "auto:all", "all:auto", "" -> formats;
            default -> formats;
        };
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : resolvedFormats.split("[,;]")) {
            String key = formatKey(part);
            if (key != null && seen.add(key)) {
                out.add(key);
            }
        }
        return out;
    }

    private static String fastFormatKey(LocalModelIndex.Entry entry) {
        if (entry == null) {
            return null;
        }
        String key = formatKey(entry.format);
        if (key != null) {
            return key;
        }
        return formatKey(entry.path);
    }

    private static String formatKey(String value) {
        String normalized = normalize(value);
        if (normalized.equals("gguf") || normalized.endsWith(".gguf")) {
            return "gguf";
        }
        if (normalized.equals("litert") || normalized.equals("litertlm") || normalized.equals("lite-rt")
                || normalized.equals("task") || normalized.equals("tflite") || normalized.endsWith(".litertlm")
                || normalized.endsWith(".task") || normalized.endsWith(".tflite")) {
            return "litert";
        }
        return null;
    }

    private static boolean hasUsableFastPath(LocalModelIndex.Entry entry, String key) {
        if (entry.path == null || entry.path.isBlank()) {
            return false;
        }
        Path path = Path.of(entry.path).toAbsolutePath().normalize();
        if ("gguf".equals(key)) {
            return hasFileWithExtension(path, ".gguf");
        }
        if ("litert".equals(key)) {
            return hasFileWithExtension(path, ".litertlm");
        }
        return false;
    }

    private static boolean hasFileWithExtension(Path path, String extension) {
        try {
            if (Files.isRegularFile(path)) {
                return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension);
            }
            if (!Files.isDirectory(path)) {
                return false;
            }
            try (var stream = Files.list(path)) {
                return stream.anyMatch(candidate -> Files.isRegularFile(candidate)
                        && candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension));
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isPreferred(LocalModelIndex.Entry entry) {
        return "local".equalsIgnoreCase(entry.source)
                && entry.path != null
                && entry.path.replace('\\', '/').contains("/models/blobs/");
    }

    private static String modelRef(LocalModelIndex.Entry entry) {
        if (entry == null) {
            return null;
        }
        if (hasText(entry.shortId)) {
            return entry.shortId;
        }
        if (hasText(entry.id)) {
            return entry.id;
        }
        if (hasText(entry.name)) {
            return entry.name;
        }
        return hasText(entry.path) ? entry.path : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stringSetting(String property, String env, String legacyEnv, String fallback) {
        String value = System.getProperty(property);
        if (hasText(value)) {
            return value;
        }
        value = System.getenv(env);
        if (hasText(value)) {
            return value;
        }
        value = System.getenv(legacyEnv);
        return hasText(value) ? value : fallback;
    }

    private static int intSetting(String property, String env, String legacyEnv, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(stringSetting(property, env, legacyEnv, Integer.toString(fallback))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

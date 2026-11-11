package tech.kayys.aqli.cli.commands;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Immutable snapshot of MOSS TTS and ONNX cache-related CLI configuration. */
record MossTtsCacheSettings(
        boolean optimizedGraphCacheEnabled,
        Path optimizedGraphCacheRoot,
        String onnxRuntimeLogLevel,
        boolean sessionCacheEnabled,
        int sessionCacheMaxEntries,
        boolean assetCacheEnabled,
        int assetCacheMaxEntries) {

    private static final String GRAPH_CACHE_ENABLED_PROPERTY = "aqli.onnx.optimized-model-cache.enabled";
    private static final String GRAPH_CACHE_ENABLED_ENV = "AQLI_ONNX_OPTIMIZED_MODEL_CACHE";
    private static final String GRAPH_CACHE_DIR_PROPERTY = "aqli.onnx.optimized-model-cache.dir";
    private static final String GRAPH_CACHE_DIR_ENV = "AQLI_ONNX_OPTIMIZED_MODEL_CACHE_DIR";
    private static final String SESSION_CACHE_ENABLED_PROPERTY = "aqli.onnx.moss-tts.session-cache.enabled";
    private static final String SESSION_CACHE_ENABLED_ENV = "AQLI_ONNX_MOSS_TTS_SESSION_CACHE";
    private static final String SESSION_CACHE_MAX_PROPERTY = "aqli.onnx.moss-tts.session-cache.max-entries";
    private static final String SESSION_CACHE_MAX_ENV = "AQLI_ONNX_MOSS_TTS_SESSION_CACHE_MAX_ENTRIES";
    private static final String ASSET_CACHE_ENABLED_PROPERTY = "aqli.onnx.moss-tts.asset-cache.enabled";
    private static final String ASSET_CACHE_ENABLED_ENV = "AQLI_ONNX_MOSS_TTS_ASSET_CACHE";
    private static final String ASSET_CACHE_MAX_PROPERTY = "aqli.onnx.moss-tts.asset-cache.max-entries";
    private static final String ASSET_CACHE_MAX_ENV = "AQLI_ONNX_MOSS_TTS_ASSET_CACHE_MAX_ENTRIES";
    private static final String ONNX_LOG_LEVEL_PROPERTY = "aqli.onnx.log-level";
    private static final String ONNX_LOG_LEVEL_ENV = "AQLI_ONNX_LOG_LEVEL";

    static MossTtsCacheSettings load() {
        Map<String, String> properties = System.getProperties().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> String.valueOf(entry.getValue())));
        return from(properties, System.getenv(), System.getProperty("user.home"));
    }

    static MossTtsCacheSettings from(
            Map<String, String> properties,
            Map<String, String> environment,
            String userHome) {
        String configuredRoot = firstNonBlank(
                properties.get(GRAPH_CACHE_DIR_PROPERTY),
                environment.get(GRAPH_CACHE_DIR_ENV));
        Path cacheRoot = configuredRoot.isBlank()
                ? Path.of(userHome, ".aqli", "cache", "onnx", "optimized", "moss-tts")
                : Path.of(configuredRoot).resolve("moss-tts");
        return new MossTtsCacheSettings(
                booleanValue(properties, environment, GRAPH_CACHE_ENABLED_PROPERTY, GRAPH_CACHE_ENABLED_ENV, true),
                cacheRoot,
                logLevel(properties, environment),
                booleanValue(properties, environment, SESSION_CACHE_ENABLED_PROPERTY, SESSION_CACHE_ENABLED_ENV, true),
                integerValue(properties, environment, SESSION_CACHE_MAX_PROPERTY, SESSION_CACHE_MAX_ENV, 2),
                booleanValue(properties, environment, ASSET_CACHE_ENABLED_PROPERTY, ASSET_CACHE_ENABLED_ENV, true),
                integerValue(properties, environment, ASSET_CACHE_MAX_PROPERTY, ASSET_CACHE_MAX_ENV, 4));
    }

    private static boolean booleanValue(
            Map<String, String> properties, Map<String, String> environment,
            String property, String env, boolean fallback) {
        String raw = firstNonBlank(properties.get(property), environment.get(env), String.valueOf(fallback));
        return !Set.of("0", "false", "no", "off", "disable", "disabled")
                .contains(raw.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static int integerValue(
            Map<String, String> properties, Map<String, String> environment,
            String property, String env, int fallback) {
        String raw = firstNonBlank(properties.get(property), environment.get(env), String.valueOf(fallback));
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String logLevel(Map<String, String> properties, Map<String, String> environment) {
        String raw = firstNonBlank(
                properties.get(ONNX_LOG_LEVEL_PROPERTY), environment.get(ONNX_LOG_LEVEL_ENV), "error");
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "verbose", "info", "warning", "warn", "error", "fatal" -> normalized;
            default -> "error";
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}

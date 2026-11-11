package tech.kayys.aqli.cli.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Resolves configuration for the standalone GGUF command from system properties
 * and its supported environment-variable names.
 *
 * <p>System properties take precedence over environment variables. The legacy
 * environment aliases are retained for compatibility with existing launchers.</p>
 */
final class GgufStandaloneConfiguration {
    private static final String PROPERTY_PREFIX = "aqli.gguf.fast_run.";
    private static final GgufStandaloneConfiguration SYSTEM =
            new GgufStandaloneConfiguration(System::getProperty, System::getenv);

    private final Function<String, String> properties;
    private final Function<String, String> environment;

    GgufStandaloneConfiguration(
            Function<String, String> properties,
            Function<String, String> environment) {
        this.properties = properties;
        this.environment = environment;
    }

    static GgufStandaloneConfiguration system() {
        return SYSTEM;
    }

    String value(String property) {
        String configured = properties.apply(property);
        if (isConfigured(configured)) {
            return configured;
        }
        for (String envName : environmentNames(property)) {
            configured = environment.apply(envName);
            if (isConfigured(configured)) {
                return configured;
            }
        }
        return null;
    }

    String string(String property, String fallback) {
        String configured = value(property);
        return isConfigured(configured) ? configured : fallback;
    }

    boolean booleanValue(String property, boolean fallback) {
        String configured = value(property);
        if (!isConfigured(configured)) {
            return fallback;
        }
        return switch (configured.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> fallback;
        };
    }

    int intValue(String property, int fallback) {
        String configured = value(property);
        if (!isConfigured(configured)) {
            return fallback;
        }
        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    int positiveInt(String property, int fallback) {
        return parsePositiveInt(value(property), fallback);
    }

    long longValue(String property, long fallback) {
        String configured = value(property);
        if (!isConfigured(configured)) {
            return fallback;
        }
        try {
            return Long.parseLong(configured.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static List<String> environmentNames(String property) {
        List<String> names = new ArrayList<>();
        addEnvironmentName(names, environmentName(property));
        if (property != null && property.startsWith(PROPERTY_PREFIX)) {
            addEnvironmentName(names,
                    "AQLI_GGUF_FAST_" + environmentName(property.substring(PROPERTY_PREFIX.length())));
        }
        addLegacyEnvironmentNames(names, property);
        return List.copyOf(names);
    }

    private static void addLegacyEnvironmentNames(List<String> names, String property) {
        if (property == null) {
            return;
        }
        switch (property) {
            case "aqli.gguf.fast_run.gpu_layers" -> addEnvironmentName(names, "GGUF_GPU_LAYERS");
            case "aqli.gguf.fast_run.batch" -> addEnvironmentName(names, "GGUF_BATCH_SIZE");
            case "aqli.gguf.fast_run.ubatch" -> {
                addEnvironmentName(names, "GGUF_UBATCH_SIZE");
                addEnvironmentName(names, "GGUF_MICRO_BATCH_SIZE");
            }
            case "aqli.gguf.fast_run.threads" -> addEnvironmentName(names, "GGUF_THREADS");
            case "aqli.gguf.fast_run.batch_threads" -> addEnvironmentName(names, "GGUF_BATCH_THREADS");
            case "aqli.gguf.fast_run.context" -> {
                addEnvironmentName(names, "GGUF_CONTEXT");
                addEnvironmentName(names, "GGUF_CONTEXT_SIZE");
                addEnvironmentName(names, "GGUF_CTX_SIZE");
            }
            case "aqli.gguf.fast_run.llama_lib_dir" -> addEnvironmentName(names, "AQLI_LLAMA_LIB_DIR");
            case "aqli.gguf.fast_run.bridge" -> addEnvironmentName(names, "AQLI_GGUF_FAST_BRIDGE");
            default -> {
            }
        }
    }

    private static void addEnvironmentName(List<String> names, String name) {
        if (name != null && !name.isBlank() && !names.contains(name)) {
            names.add(name);
        }
    }

    private static String environmentName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return normalized.replaceAll("^_+|_+$", "");
    }

    static int parsePositiveInt(String value, int fallback) {
        if (!isConfigured(value)) {
            return Math.max(1, fallback);
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return Math.max(1, fallback);
        }
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}

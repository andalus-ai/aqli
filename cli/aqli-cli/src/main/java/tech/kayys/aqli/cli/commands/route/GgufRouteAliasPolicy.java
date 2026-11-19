package tech.kayys.aqli.cli.commands.route;

import java.util.Locale;

/**
 * Recognizes legacy and explicit provider/plugin names that select the standalone GGUF route.
 *
 * <p>This is CLI compatibility policy only; it does not select an execution backend.</p>
 */
public final class GgufRouteAliasPolicy {

    private GgufRouteAliasPolicy() {
    }

    public static boolean isProviderAlias(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("gguf")
                || normalized.equals("llamacpp")
                || normalized.equals("llama.cpp")
                || normalized.equals("llama-cpp")
                || normalized.equals("java")
                || normalized.equals("java-native")
                || normalized.equals("jvm");
    }

    public static boolean isPluginAlias(String plugin) {
        if (plugin == null || plugin.isBlank()) {
            return false;
        }
        String normalized = plugin.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("gguf")
                || normalized.equals("gguf-runner")
                || normalized.equals("llamacpp")
                || normalized.equals("llama.cpp")
                || normalized.equals("llama-cpp");
    }
}

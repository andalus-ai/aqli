package tech.kayys.aqli.cli.commands.route;

import java.util.Locale;

/**
 * Maps shared engine hints to the execution modes supported for Safetensors checkpoints.
 *
 * <p>A {@code null} result means the hint does not explicitly select either compatibility route;
 * normal runtime routing should decide.</p>
 */
public final class SafetensorEngineHintPolicy {

    private SafetensorEngineHintPolicy() {
    }

    public static String backendFor(String engineHint) {
        if (engineHint == null) {
            return null;
        }
        return switch (engineHint.trim().toLowerCase(Locale.ROOT)) {
            case "java", "java-native", "native", "direct" -> "direct";
            case "llamacpp", "llama.cpp", "llama-cpp", "gguf" -> "gguf";
            default -> null;
        };
    }
}

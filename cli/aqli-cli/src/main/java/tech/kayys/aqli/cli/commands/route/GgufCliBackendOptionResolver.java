package tech.kayys.aqli.cli.commands.route;

/**
 * Resolves CLI GGUF backend options and inherited platform defaults.
 *
 * <p>This only resolves option precedence; runtime backend discovery and selection remain elsewhere.</p>
 */
public final class GgufCliBackendOptionResolver {

    private GgufCliBackendOptionResolver() {
    }

    public static String resolve(String explicitBackend, boolean useCpu, String platform) {
        if (explicitBackend != null && !explicitBackend.isBlank()) {
            return explicitBackend.trim();
        }
        if (useCpu) {
            return "cpu";
        }
        if (platform != null && !platform.isBlank()) {
            return platform.trim();
        }
        return null;
    }
}

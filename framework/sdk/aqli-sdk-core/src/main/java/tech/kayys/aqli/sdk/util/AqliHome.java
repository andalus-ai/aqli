package tech.kayys.aqli.sdk.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves Aqli runtime home with Aqli-first defaults.
 *
 * <p>Centralizes the public Aqli home contract for the CLI, SDK, runners, and
 * embedding applications. Host products can set {@code aqli.home} or
 * {@code AQLI_HOME}; otherwise Aqli uses {@code ~/.aqli}.
 */
public final class AqliHome {
    private static final String AQLI_HOME_PROP = "aqli.home";

    private AqliHome() {
    }

    public static Path resolve() {
        String explicit = firstNonBlank(
                System.getProperty(AQLI_HOME_PROP),
                System.getenv("AQLI_HOME"));
        if (hasText(explicit)) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }

        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".aqli").toAbsolutePath().normalize();
    }

    public static Path path(String... parts) {
        Path root = resolve();
        for (String part : parts) {
            root = root.resolve(part);
        }
        return root;
    }

    public static void applySystemProperties() {
        Path resolved = resolve();
        if (!hasText(System.getProperty(AQLI_HOME_PROP))) {
            System.setProperty(AQLI_HOME_PROP, resolved.toString());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

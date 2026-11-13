package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Common path expansion and display utilities for CLI commands.
 */
final class CliPathUtils {

    private CliPathUtils() {
    }

    static Path expandCliPath(String value) {
        if (value == null || value.isBlank()) {
            return Path.of("");
        }
        if (value.equals("~")) {
            return Path.of(System.getProperty("user.home", "~"));
        }
        if (value.startsWith("~/") || value.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home", "~")).resolve(value.substring(2));
        }
        return Path.of(value);
    }

    static Path existingCliPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Path path = expandCliPath(value.trim()).toAbsolutePath().normalize();
            return Files.exists(path) ? path : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static String displayPath(Path path) {
        if (path == null) {
            return "";
        }
        String value = path.toAbsolutePath().normalize().toString();
        String userHome = System.getProperty("user.home");
        if (userHome != null && value.startsWith(userHome)) {
            return "~" + value.substring(userHome.length());
        }
        return value;
    }
}

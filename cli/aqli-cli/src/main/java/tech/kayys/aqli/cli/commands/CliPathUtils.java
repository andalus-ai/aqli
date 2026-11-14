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

    static Path replaceExtension(Path path, String newExt) {
        if (path == null) {
            return null;
        }
        String filename = path.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) return path.resolveSibling(filename + "." + newExt);
        return path.resolveSibling(filename.substring(0, lastDot) + "." + newExt);
    }

    static String normalizeExtension(String ext) {
        if (ext == null) {
            return "";
        }
        String value = ext.trim().toLowerCase(java.util.Locale.ROOT);
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        return value;
    }

    static String pathExtension(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot + 1 < filename.length() ? filename.substring(dot + 1) : "";
    }

    static boolean isKnownAudioExtension(String ext) {
        if (ext == null || ext.isBlank()) {
            return false;
        }
        return java.util.Set.of("wav", "mp3", "flac", "ogg", "m4a", "aac").contains(ext.toLowerCase(java.util.Locale.ROOT));
    }
}

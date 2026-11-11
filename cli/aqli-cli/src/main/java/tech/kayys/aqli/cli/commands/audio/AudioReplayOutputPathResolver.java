package tech.kayys.aqli.cli.commands.audio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Resolves a collision-free output path for an audio replay artifact. */
public final class AudioReplayOutputPathResolver {
    private AudioReplayOutputPathResolver() {
    }

    public static Path resolve(String originalOutputPath, String replayAudioFormat) {
        String extension = normalizeExtension(replayAudioFormat);
        Path original = originalOutputPath == null || originalOutputPath.isBlank()
                ? Path.of("output." + (extension.isBlank() ? "wav" : extension))
                : Path.of(originalOutputPath);
        Path replay = appendReplaySuffix(original, extension);
        return unique(replay);
    }

    private static Path appendReplaySuffix(Path path, String fallbackExtension) {
        String filename = path.getFileName() == null ? "output" : path.getFileName().toString();
        String extension = extension(path);
        if (extension.isBlank()) {
            extension = normalizeExtension(fallbackExtension);
        }
        String base = stripExtension(filename, extension);
        return path.resolveSibling(base + "-replay" + (extension.isBlank() ? "" : "." + extension));
    }

    private static Path unique(Path path) {
        if (!Files.exists(path)) {
            return path;
        }
        String filename = path.getFileName() == null ? "output" : path.getFileName().toString();
        String extension = extension(path);
        String base = stripExtension(filename, extension);
        for (int index = 2; index < 1000; index++) {
            Path candidate = path.resolveSibling(base + "-" + index
                    + (extension.isBlank() ? "" : "." + extension));
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return path;
    }

    private static String stripExtension(String filename, String extension) {
        if (!extension.isBlank() && filename.toLowerCase(Locale.ROOT)
                .endsWith("." + extension.toLowerCase(Locale.ROOT))) {
            return filename.substring(0, filename.length() - extension.length() - 1);
        }
        return filename;
    }

    private static String extension(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot <= 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1);
    }

    private static String normalizeExtension(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}

package tech.kayys.aqli.cli.commands;

import java.nio.file.Path;
import java.util.Locale;

/** Resolves media output paths from CLI overrides and provider-reported formats. */
final class MediaOutputPathResolver {
    private MediaOutputPathResolver() {
    }

    static Resolution resolve(Request request) {
        String actualExtension = normalize(request.actualExtension());
        String requestedExtension = normalize(request.requestedExtension());
        String extension = requestedExtension.isEmpty() ? actualExtension : requestedExtension;
        boolean overridden = request.audio()
                && !requestedExtension.isEmpty()
                && !requestedExtension.equalsIgnoreCase(actualExtension);
        if (overridden) {
            extension = actualExtension;
        }

        String requestedPath = request.outputPath();
        Path path = requestedPath != null && !requestedPath.isEmpty()
                ? Path.of(requestedPath)
                : Path.of("output." + extension);
        if (request.requestedExtension() != null && !request.requestedExtension().isBlank()) {
            path = replaceExtension(path, extension);
        } else if (request.audio()
                && requestedPath != null
                && !requestedPath.isEmpty()
                && isKnownAudioExtension(extensionOf(path))
                && !extensionOf(path).equalsIgnoreCase(extension)) {
            path = replaceExtension(path, extension);
        }
        return new Resolution(path, extension, requestedExtension, overridden);
    }

    private static String normalize(String extension) {
        if (extension == null) {
            return "";
        }
        String value = extension.trim().toLowerCase(Locale.ROOT);
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        return value;
    }

    private static String extensionOf(Path path) {
        Path filename = path.getFileName();
        if (filename == null) {
            return "";
        }
        String name = filename.toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : normalize(name.substring(dot + 1));
    }

    private static boolean isKnownAudioExtension(String extension) {
        return switch (normalize(extension)) {
            case "aac", "flac", "m4a", "mp3", "ogg", "wav" -> true;
            default -> false;
        };
    }

    private static Path replaceExtension(Path path, String extension) {
        Path filename = path.getFileName();
        if (filename == null) {
            return path;
        }
        String name = filename.toString();
        int dot = name.lastIndexOf('.');
        String replacement = dot < 0 ? name + "." + extension : name.substring(0, dot) + "." + extension;
        return path.resolveSibling(replacement);
    }

    record Request(String outputPath, String requestedExtension, String actualExtension, boolean audio) {
    }

    record Resolution(Path path, String extension, String requestedExtension, boolean requestedExtensionOverridden) {
    }
}

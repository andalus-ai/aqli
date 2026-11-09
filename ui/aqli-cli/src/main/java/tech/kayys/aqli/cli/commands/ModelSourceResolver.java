package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Classifies the origin of a model reference without downloading or resolving artifacts.
 *
 * <p>Repository references are identifiers, not local paths. Actual cache/index lookup and
 * Hugging Face download policy remain with the run workflow.</p>
 */
final class ModelSourceResolver {

    enum Kind {
        LOCAL_FILE,
        LOCAL_DIRECTORY,
        HUGGING_FACE_REPOSITORY,
        UNRESOLVED_REFERENCE
    }

    record Source(Kind kind, String reference, Path localPath) {
    }

    private ModelSourceResolver() {
    }

    static Source classify(String reference) {
        if (reference == null || reference.isBlank()) {
            return new Source(Kind.UNRESOLVED_REFERENCE, reference, null);
        }

        String value = reference.trim();
        Path path = existingPath(value);
        if (path != null) {
            if (Files.isDirectory(path)) {
                return new Source(Kind.LOCAL_DIRECTORY, value, path);
            }
            if (Files.isRegularFile(path)) {
                return new Source(Kind.LOCAL_FILE, value, path);
            }
        }

        String repositoryId = huggingFaceRepositoryId(value);
        if (repositoryId != null) {
            return new Source(Kind.HUGGING_FACE_REPOSITORY, repositoryId, null);
        }
        return new Source(Kind.UNRESOLVED_REFERENCE, value, null);
    }

    /**
     * Returns a Hugging Face owner/repository identifier for a supported model reference.
     * This deliberately performs syntax validation only; it does not access the network.
     */
    static String huggingFaceRepositoryId(String requestedModel) {
        String value = stripHuggingFacePrefix(requestedModel);
        if (value.isBlank()
                || value.contains("://")
                || value.startsWith("/")
                || value.startsWith(".")
                || value.startsWith("~")
                || value.contains("\\\\")
                || !value.contains("/")) {
            return null;
        }
        String[] parts = value.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }
        return value;
    }

    private static String stripHuggingFacePrefix(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("hf:")) {
            return trimmed.substring("hf:".length()).trim();
        }
        if (trimmed.startsWith("huggingface:")) {
            return trimmed.substring("huggingface:".length()).trim();
        }
        return trimmed;
    }

    private static Path existingPath(String value) {
        try {
            Path path = Path.of(value);
            return Files.exists(path) ? path.toAbsolutePath().normalize() : null;
        } catch (InvalidPathException ignored) {
            return null;
        }
    }
}

package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Resolves an already-indexed local artifact for a model repository reference.
 *
 * <p>The index remains responsible for persistence and disk scanning. This resolver owns
 * reference lookup order and rejects entries whose artifact path is no longer present.</p>
 */
final class LocalModelIndexResolver {

    @FunctionalInterface
    interface Lookup {
        Optional<LocalModelIndex.Entry> find(String reference, String preferredFormat);
    }

    private LocalModelIndexResolver() {
    }

    static Optional<LocalModelIndex.Entry> find(
            String repositoryId,
            String preferredFormat,
            Lookup lookup) {
        if (repositoryId == null || repositoryId.isBlank() || lookup == null) {
            return Optional.empty();
        }

        for (String reference : List.of(repositoryId, "hf:" + repositoryId)) {
            try {
                Optional<LocalModelIndex.Entry> candidate = lookup.find(reference, preferredFormat);
                if (candidate.isPresent() && hasExistingArtifact(candidate.get())) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Best effort lookup; the caller owns the final user-facing diagnostic.
            }
        }
        return Optional.empty();
    }

    private static boolean hasExistingArtifact(LocalModelIndex.Entry entry) {
        if (entry == null || entry.path == null || entry.path.isBlank()) {
            return false;
        }
        try {
            return Files.exists(Path.of(entry.path));
        } catch (InvalidPathException ignored) {
            return false;
        }
    }
}

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

    /**
     * Looks up one exact index reference without applying alias expansion or artifact validation.
     * The caller may need the indexed metadata (for example, a disabled entry) even when its
     * artifact is currently missing, and remains responsible for deciding how to handle it.
     */
    static Optional<LocalModelIndex.Entry> findExactReference(
            String reference,
            String preferredFormat,
            Lookup lookup) {
        if (reference == null || reference.isBlank() || lookup == null) {
            return Optional.empty();
        }
        try {
            return lookup.find(reference, preferredFormat);
        } catch (Exception ignored) {
            // The caller owns the final user-facing diagnostic or fallback behavior.
            return Optional.empty();
        }
    }

    /**
     * Resolves one reference using a preferred-format lookup, then verifies the
     * unfiltered result has that format. Unlike {@link #find}, this does not
     * add an {@code hf:} alias; callers use it when exact-reference semantics
     * are part of the existing CLI contract.
     */
    static Optional<LocalModelIndex.Entry> findExactFormat(
            String reference,
            String preferredFormat,
            Lookup lookup) {
        if (reference == null || reference.isBlank()
                || preferredFormat == null || preferredFormat.isBlank()
                || lookup == null) {
            return Optional.empty();
        }

        try {
            Optional<LocalModelIndex.Entry> preferred = lookup.find(reference, preferredFormat);
            if (preferred.isPresent() && hasExistingArtifact(preferred.get())) {
                return preferred;
            }
        } catch (Exception ignored) {
            // Preserve the fallback to an unfiltered index lookup.
        }

        try {
            Optional<LocalModelIndex.Entry> fallback = lookup.find(reference, null);
            if (fallback.isPresent()
                    && preferredFormat.equalsIgnoreCase(fallback.get().format)
                    && hasExistingArtifact(fallback.get())) {
                return fallback;
            }
        } catch (Exception ignored) {
            // The caller owns the final user-facing diagnostic.
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

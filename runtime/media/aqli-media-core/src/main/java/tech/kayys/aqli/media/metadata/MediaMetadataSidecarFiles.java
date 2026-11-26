package tech.kayys.aqli.media.metadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Shared filesystem operations for durable media metadata sidecar persistence. */
public final class MediaMetadataSidecarFiles {

    private MediaMetadataSidecarFiles() {
    }

    /**
     * Writes UTF-8 content to a sibling temporary file before replacing the destination.
     * The temporary file is kept on the same filesystem so an atomic move can be attempted.
     */
    public static void writeAtomically(Path destination, String content) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || target.getFileName() == null) {
            throw new IOException("sidecar destination must be a file path: " + destination);
        }

        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
        boolean replaced = false;
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}

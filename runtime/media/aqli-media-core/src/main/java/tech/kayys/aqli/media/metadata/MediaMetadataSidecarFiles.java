package tech.kayys.aqli.media.metadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Shared filesystem operations for durable media metadata sidecar persistence. */
public final class MediaMetadataSidecarFiles {

    private MediaMetadataSidecarFiles() {
    }

    /**
     * Writes UTF-8 content to a sibling temporary file before replacing the destination.
     * The temporary file is kept on the same filesystem so an atomic move can be attempted.
     * If atomic moves are unsupported, replacement falls back to a regular move.
     */
    public static void writeAtomically(Path destination, String content) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || target.getFileName() == null) {
            throw new IOException("sidecar destination must be a file path: " + destination);
        }

        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
        try {
            byte[] encoded = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
}

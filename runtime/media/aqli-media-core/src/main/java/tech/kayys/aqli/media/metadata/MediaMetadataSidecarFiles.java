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
import java.util.Objects;

/** Shared filesystem operations for media metadata sidecar persistence. */
public final class MediaMetadataSidecarFiles {

    private static final MoveStrategy FILE_SYSTEM_MOVE = new MoveStrategy() {
        @Override
        public void atomicMove(Path source, Path target) throws IOException {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void replace(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    };

    private MediaMetadataSidecarFiles() {
    }

    /**
     * Writes UTF-8 content to a sibling temporary file before replacing the destination.
     * The temporary file is kept on the same filesystem so an atomic move can be attempted.
     * If atomic moves are unsupported, replacement falls back to a regular, non-atomic move.
     *
     * <p>The temporary file contents are forced before replacement. The parent directory is not
     * synchronized, so this method does not promise directory-entry durability across power loss.
     */
    public static void writeWithAtomicMoveFallback(Path destination, String content) throws IOException {
        writeWithAtomicMoveFallback(destination, content, FILE_SYSTEM_MOVE);
    }

    static void writeWithAtomicMoveFallback(Path destination, String content, MoveStrategy moveStrategy)
            throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(moveStrategy, "moveStrategy");

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
                moveStrategy.atomicMove(temporary, target);
            } catch (AtomicMoveNotSupportedException unsupported) {
                moveStrategy.replace(temporary, target);
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

    interface MoveStrategy {
        void atomicMove(Path source, Path target) throws IOException;

        void replace(Path source, Path target) throws IOException;
    }
}

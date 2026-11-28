package tech.kayys.aqli.media.metadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/** Shared filesystem operations for media metadata sidecar persistence. */
public final class MediaMetadataSidecarFiles {

    private static final ReentrantLock[] SIDE_CAR_LOCKS = new ReentrantLock[64];

    static {
        Arrays.setAll(SIDE_CAR_LOCKS, ignored -> new ReentrantLock());
    }

    private static final FileOperations FILE_OPERATIONS = new FileOperations() {
        @Override
        public void atomicMove(Path source, Path target) throws IOException {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void replace(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void deleteTemporary(Path temporary) throws IOException {
            Files.deleteIfExists(temporary);
        }
    };

    private MediaMetadataSidecarFiles() {
    }

    /**
     * Writes UTF-8 content to a sibling temporary file before replacing the destination.
     * The temporary file is kept on the same filesystem so an atomic move can be attempted.
     * If atomic moves are unsupported, replacement falls back to a regular, non-atomic move.
     *
     * <p>Each invocation creates its own sibling temporary file, so concurrent invocations do not
     * share temporary state. When multiple invocations target the same destination, each successful
     * replacement publishes one complete payload; the last successful move wins. This method does
     * not serialize callers or protect read-modify-write sequences performed before this method is
     * called. Callers that need to prevent lost updates must coordinate the complete transaction.
     *
     * <p>The temporary file contents are forced before replacement. The parent directory is not
     * synchronized, so this method does not promise directory-entry durability across power loss.
     */
    public static void writeWithAtomicMoveFallback(Path destination, String content) throws IOException {
        writeWithAtomicMoveFallback(destination, content, FILE_OPERATIONS);
    }

    /**
     * Creates the destination's parent directories for a sidecar write.
     * Read-only operations must not call this method.
     */
    public static void createSidecarParentDirectories(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || target.getFileName() == null) {
            throw new IOException("sidecar destination must be a file path: " + destination);
        }
        Files.createDirectories(parent);
    }

    /**
     * Runs a complete sidecar operation under a JVM-local and operating-system file lock.
     * The hidden sibling lock file is intentionally retained: deleting it can split concurrent
     * processes across different inodes. Network filesystems may provide different lock semantics.
     */
    public static <T> T withSidecarLock(Path destination, SidecarOperation<T> operation) throws Exception {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(operation, "operation");
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || target.getFileName() == null) {
            throw new IOException("sidecar destination must be a file path: " + destination);
        }
        if (!Files.isDirectory(parent)) {
            throw new IOException("sidecar parent directory does not exist: " + parent);
        }

        Path lockPath = parent.resolve("." + target.getFileName() + ".lock");
        ReentrantLock processLock = SIDE_CAR_LOCKS[Math.floorMod(target.hashCode(), SIDE_CAR_LOCKS.length)];
        processLock.lock();
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return operation.execute();
        } finally {
            processLock.unlock();
        }
    }

    @FunctionalInterface
    public interface SidecarOperation<T> {
        T execute() throws Exception;
    }

    static void writeWithAtomicMoveFallback(Path destination, String content, FileOperations fileOperations)
            throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(fileOperations, "fileOperations");

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
                fileOperations.atomicMove(temporary, target);
            } catch (AtomicMoveNotSupportedException unsupported) {
                fileOperations.replace(temporary, target);
            }
        } catch (IOException | RuntimeException | Error failure) {
            try {
                fileOperations.deleteTemporary(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    interface FileOperations {
        void atomicMove(Path source, Path target) throws IOException;

        void replace(Path source, Path target) throws IOException;

        void deleteTemporary(Path temporary) throws IOException;
    }
}

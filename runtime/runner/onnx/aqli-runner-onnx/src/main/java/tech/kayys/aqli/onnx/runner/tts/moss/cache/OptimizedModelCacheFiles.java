package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/** Safe staging helpers for external-data files used by optimized ONNX graphs. */
public final class OptimizedModelCacheFiles {
    private OptimizedModelCacheFiles() {
    }

    /**
     * Removes staging files left behind by a process that terminated while publishing
     * a sidecar. Call only while holding the cache-root lock.
     */
    public static int cleanupAbandonedStages(Path cacheRoot) {
        Path requestedRoot = cacheRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(requestedRoot)) {
            return 0;
        }
        try {
            Path root = requestedRoot.toRealPath();
            try (Stream<Path> paths = Files.walk(root, 2)) {
                return paths.filter(path -> !path.equals(root))
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith(".moss-sidecar-") && name.endsWith(".tmp");
                        })
                        .mapToInt(path -> {
                            try {
                                return Files.deleteIfExists(path) ? 1 : 0;
                            } catch (IOException ignored) {
                                return 0;
                            }
                        })
                        .sum();
            }
        } catch (IOException ignored) {
            return 0;
        }
    }

    /**
     * Reuses an identical sidecar, otherwise stages a hard link or complete copy
     * before replacing the published target. Readers never observe a partially
     * copied target file.
     */
    public static void linkOrCopySidecar(Path source, Path target) throws IOException {
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && Files.mismatch(source, target) == -1L) {
            return;
        }
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("Sidecar target has no parent directory: " + target);
        }
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".moss-sidecar-", ".tmp");
        try {
            Files.delete(staged);
            try {
                Files.createLink(staged, source);
            } catch (IOException | UnsupportedOperationException | SecurityException linkFailure) {
                Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}

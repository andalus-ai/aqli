package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coordinates optimized-cache creation and maintenance across threads and processes.
 *
 * <p>The lock file lives beside the cache root, not inside it, so cache inventory
 * and pruning never mistake coordination files for model artifacts.</p>
 */
public final class OptimizedModelCacheLock {
    private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private OptimizedModelCacheLock() {
    }

    public static <T, E extends Exception> T withLock(Path cacheRoot, CheckedOperation<T, E> operation) throws E {
        Path requestedRoot = cacheRoot.toAbsolutePath().normalize();
        Path requestedParent = requestedRoot.getParent();
        if (requestedParent == null || requestedRoot.getFileName() == null) {
            throw new IllegalArgumentException("Cache root must have a parent directory: " + requestedRoot);
        }
        Path normalizedRoot;
        try {
            Files.createDirectories(requestedParent);
            normalizedRoot = Files.exists(requestedRoot)
                    ? requestedRoot.toRealPath()
                    : requestedParent.toRealPath().resolve(requestedRoot.getFileName());
        } catch (IOException e) {
            throw lockFailure(requestedRoot, e);
        }

        Path lockFile = normalizedRoot.getParent().resolve(lockFileName(normalizedRoot));
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockFile, ignored -> new ReentrantLock());
        jvmLock.lock();
        try {
            FileChannel channel;
            try {
                channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw lockFailure(normalizedRoot, e);
            }
            FileLock fileLock;
            try {
                fileLock = channel.lock();
            } catch (IOException e) {
                closeQuietly(channel);
                throw lockFailure(normalizedRoot, e);
            } catch (RuntimeException | Error failure) {
                closeQuietly(channel);
                throw failure;
            }
            try {
                return operation.run();
            } finally {
                closeQuietly(fileLock);
                closeQuietly(channel);
            }
        } finally {
            jvmLock.unlock();
        }
    }

    private static UncheckedIOException lockFailure(Path cacheRoot, IOException cause) {
        return new UncheckedIOException("Unable to coordinate optimized ONNX cache at " + cacheRoot, cause);
    }

    private static void closeQuietly(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception ignored) {
            // Releasing the OS lock is best-effort during cleanup.
        }
    }

    private static String lockFileName(Path cacheRoot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(cacheRoot.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ".moss-optimized-cache-" + HexFormat.of().formatHex(digest.digest(), 0, 12) + ".lock";
        } catch (Exception e) {
            throw new IllegalStateException("Unable to identify optimized ONNX cache lock", e);
        }
    }

    @FunctionalInterface
    public interface CheckedOperation<T, E extends Exception> {
        T run() throws E;
    }
}

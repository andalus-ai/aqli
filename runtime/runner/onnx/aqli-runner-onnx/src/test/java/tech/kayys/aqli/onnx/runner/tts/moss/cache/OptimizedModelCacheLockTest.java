package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptimizedModelCacheLockTest {
    @TempDir
    Path tempDir;

    @Test
    void serializesConcurrentThreadsForTheSameCacheRoot() throws Exception {
        Path cacheRoot = tempDir.resolve("cache");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> runLocked(cacheRoot, ready, start, active, maximumActive));
            var second = executor.submit(() -> runLocked(cacheRoot, ready, start, active, maximumActive));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertEquals(1, maximumActive.get());
        assertFalse(Files.exists(cacheRoot));
    }

    @Test
    void propagatesCheckedOperationFailuresWithoutWrappingThemAsLockFailures() {
        Path cacheRoot = tempDir.resolve("cache");
        IOException expected = new IOException("operation failed");

        IOException actual = assertThrows(IOException.class, () ->
                OptimizedModelCacheLock.withLock(cacheRoot, () -> {
                    throw expected;
                }));

        assertSame(expected, actual);
    }

    @Test
    void releasesLockWhenOperationFails() throws Exception {
        Path cacheRoot = tempDir.resolve("cache");
        try {
            OptimizedModelCacheLock.withLock(cacheRoot, () -> {
                throw new IllegalStateException("expected");
            });
        } catch (IllegalStateException expected) {
            assertEquals("expected", expected.getMessage());
        }

        assertEquals("recovered", OptimizedModelCacheLock.withLock(cacheRoot, () -> "recovered"));
    }

    private static void runLocked(
            Path cacheRoot,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger active,
            AtomicInteger maximumActive) throws Exception {
        ready.countDown();
        start.await();
        OptimizedModelCacheLock.withLock(cacheRoot, () -> {
            int nowActive = active.incrementAndGet();
            maximumActive.accumulateAndGet(nowActive, Math::max);
            try {
                Thread.sleep(100);
            } finally {
                active.decrementAndGet();
            }
            return null;
        });
    }

}

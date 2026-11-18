package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GgufDaemonStartupLockTest {
    @TempDir
    Path tempDir;

    @Test
    void serializesConcurrentStartupAttemptsWithinJvm() throws Exception {
        Path lockFile = tempDir.resolve("daemon.start.lock");
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAcquired = new CountDownLatch(1);
        AtomicBoolean secondEnteredEarly = new AtomicBoolean();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                try (GgufDaemonStartupLock ignored = GgufDaemonStartupLock.acquire(lockFile)) {
                    firstAcquired.countDown();
                    assertTrue(releaseFirst.await(2, TimeUnit.SECONDS));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
            assertTrue(firstAcquired.await(2, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> {
                try (GgufDaemonStartupLock ignored = GgufDaemonStartupLock.acquire(lockFile)) {
                    secondEnteredEarly.set(releaseFirst.getCount() > 0);
                    secondAcquired.countDown();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });

            assertFalse(secondAcquired.await(100, TimeUnit.MILLISECONDS));
            assertFalse(secondEnteredEarly.get());
            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertTrue(secondAcquired.getCount() == 0);
            assertFalse(secondEnteredEarly.get());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }
}

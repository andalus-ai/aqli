package tech.kayys.aqli.media.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaMetadataSidecarFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void createsParentDirectoriesAndReplacesExistingSidecar() throws Exception {
        Path sidecar = tempDir.resolve("nested/metadata.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, "old-content");

        MediaMetadataSidecarFiles.writeWithAtomicMoveFallback(sidecar, "{\"schema_version\":1}");

        assertEquals("{\"schema_version\":1}", Files.readString(sidecar));
        try (Stream<Path> files = Files.list(sidecar.getParent())) {
            assertEquals(1, files.count(), "temporary file should be moved away");
        }
    }

    @Test
    void fallsBackToRegularReplacementWhenAtomicMoveIsUnsupported() throws Exception {
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(sidecar, "old-content");
        AtomicInteger atomicAttempts = new AtomicInteger();
        AtomicInteger replacementAttempts = new AtomicInteger();

        MediaMetadataSidecarFiles.FileOperations fileOperations = new MediaMetadataSidecarFiles.FileOperations() {
            @Override
            public void atomicMove(Path source, Path target) throws IOException {
                atomicAttempts.incrementAndGet();
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "forced by test");
            }

            @Override
            public void replace(Path source, Path target) throws IOException {
                replacementAttempts.incrementAndGet();
                Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            @Override
            public void deleteTemporary(Path temporary) throws IOException {
                Files.deleteIfExists(temporary);
            }
        };

        MediaMetadataSidecarFiles.writeWithAtomicMoveFallback(sidecar, "new-content", fileOperations);

        assertEquals(1, atomicAttempts.get());
        assertEquals(1, replacementAttempts.get());
        assertEquals("new-content", Files.readString(sidecar));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(1, files.count(), "temporary file should be moved away");
        }
    }

    @Test
    void cleansTemporaryFileAndPropagatesFallbackFailure() throws Exception {
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(sidecar, "old-content");
        IOException fallbackFailure = new IOException("forced replacement failure");
        AtomicInteger replacementAttempts = new AtomicInteger();

        MediaMetadataSidecarFiles.FileOperations fileOperations = new MediaMetadataSidecarFiles.FileOperations() {
            @Override
            public void atomicMove(Path source, Path target) throws IOException {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "forced by test");
            }

            @Override
            public void replace(Path source, Path target) throws IOException {
                replacementAttempts.incrementAndGet();
                throw fallbackFailure;
            }

            @Override
            public void deleteTemporary(Path temporary) throws IOException {
                Files.deleteIfExists(temporary);
            }
        };

        IOException thrown = assertThrows(IOException.class,
                () -> MediaMetadataSidecarFiles.writeWithAtomicMoveFallback(sidecar, "new-content", fileOperations));

        assertSame(fallbackFailure, thrown);
        assertEquals(1, replacementAttempts.get());
        assertEquals("old-content", Files.readString(sidecar));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(1, files.count(), "failed write should not leave a temporary file");
        }
    }

    @Test
    void retainsWriteFailureAndSuppressesTemporaryCleanupFailure() throws Exception {
        Path sidecar = tempDir.resolve("metadata.json");
        Files.writeString(sidecar, "old-content");
        IOException writeFailure = new IOException("forced replacement failure");
        IOException cleanupFailure = new IOException("forced temporary cleanup failure");
        AtomicReference<Path> temporaryPath = new AtomicReference<>();

        MediaMetadataSidecarFiles.FileOperations fileOperations = new MediaMetadataSidecarFiles.FileOperations() {
            @Override
            public void atomicMove(Path source, Path target) throws IOException {
                temporaryPath.set(source);
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "forced by test");
            }

            @Override
            public void replace(Path source, Path target) throws IOException {
                throw writeFailure;
            }

            @Override
            public void deleteTemporary(Path temporary) throws IOException {
                throw cleanupFailure;
            }
        };

        IOException thrown = assertThrows(IOException.class,
                () -> MediaMetadataSidecarFiles.writeWithAtomicMoveFallback(sidecar, "new-content", fileOperations));

        assertSame(writeFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(cleanupFailure, thrown.getSuppressed()[0]);
        assertEquals("old-content", Files.readString(sidecar));
        assertTrue(Files.exists(temporaryPath.get()), "failed cleanup should leave the temp file for diagnosis");
        Files.deleteIfExists(temporaryPath.get());
    }

    @Test
    void removesTemporaryFileWhenDestinationCannotBeReplaced() throws Exception {
        Path destinationDirectory = tempDir.resolve("metadata.json");
        Files.createDirectory(destinationDirectory);

        assertThrows(IOException.class,
                () -> MediaMetadataSidecarFiles.writeWithAtomicMoveFallback(destinationDirectory, "content"));

        assertTrue(Files.isDirectory(destinationDirectory));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(1, files.count(), "failed write should not leave a temporary file");
        }
    }

    @Test
    void rejectsNullInputsBeforeCreatingDirectories() {
        Path sidecar = tempDir.resolve("not-created/metadata.json");

        assertThrows(NullPointerException.class,
                () -> MediaMetadataSidecarFiles.writeWithAtomicMoveFallback(sidecar, null));
        assertThrows(NullPointerException.class,
                () -> MediaMetadataSidecarFiles.writeWithAtomicMoveFallback(null, "content"));

        assertFalse(Files.exists(sidecar.getParent()));
    }


    @Test
    void concurrentWritesUseIndependentTemporaryFilesAndPublishCompletePayloads() throws Exception {
        Path sidecar = tempDir.resolve("metadata.json");
        String firstContent = "first-" + "a".repeat(32_768);
        String secondContent = "second-" + "b".repeat(32_768);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch bothWritersReady = new CountDownLatch(2);
        Set<Path> temporaryPaths = ConcurrentHashMap.newKeySet();
        Object replacementLock = new Object();

        MediaMetadataSidecarFiles.FileOperations fileOperations = new MediaMetadataSidecarFiles.FileOperations() {
            @Override
            public void atomicMove(Path source, Path target) throws IOException {
                temporaryPaths.add(source.toAbsolutePath().normalize());
                bothWritersReady.countDown();
                try {
                    if (!bothWritersReady.await(10, TimeUnit.SECONDS)) {
                        throw new IOException("concurrent writers did not both reach replacement");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while coordinating concurrent sidecar writes", interrupted);
                }
                synchronized (replacementLock) {
                    Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }

            @Override
            public void replace(Path source, Path target) throws IOException {
                Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            @Override
            public void deleteTemporary(Path temporary) throws IOException {
                Files.deleteIfExists(temporary);
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> writeAfterStart(start, sidecar, firstContent, fileOperations));
            Future<?> second = executor.submit(() -> writeAfterStart(start, sidecar, secondContent, fileOperations));
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, temporaryPaths.size(), "each invocation should own a distinct temporary file");
        String published = Files.readString(sidecar);
        assertTrue(published.equals(firstContent) || published.equals(secondContent),
                "the destination must contain one complete writer payload");
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(1, files.count(), "successful concurrent writes should leave no temporary files");
        }
    }

    private static void writeAfterStart(
            CountDownLatch start,
            Path sidecar,
            String content,
            MediaMetadataSidecarFiles.FileOperations fileOperations) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IOException("concurrent sidecar write was not released");
            }
            MediaMetadataSidecarFiles.writeWithAtomicMoveFallback(sidecar, content, fileOperations);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted before concurrent sidecar write", interrupted);
        } catch (IOException failure) {
            throw new RuntimeException(failure);
        }
    }


    @Test
    void sidecarLockDoesNotCreateMissingParentDirectories() {
        Path sidecar = tempDir.resolve("missing/metadata.json");

        assertThrows(IOException.class,
                () -> MediaMetadataSidecarFiles.withSidecarLock(sidecar, () -> "unreachable"));

        assertFalse(Files.exists(sidecar.getParent()));
    }

    @Test
    void sidecarLockSerializesConcurrentOperationsAndRetainsLockFile() throws Exception {
        Path sidecar = tempDir.resolve("metadata.json");
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                try {
                    MediaMetadataSidecarFiles.withSidecarLock(sidecar, () -> {
                        maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                        firstEntered.countDown();
                        try {
                            if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                                throw new IOException("timed out waiting to release first operation");
                            }
                        } finally {
                            active.decrementAndGet();
                        }
                        return null;
                    });
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
            assertTrue(firstEntered.await(10, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                try {
                    MediaMetadataSidecarFiles.withSidecarLock(sidecar, () -> {
                        maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                        secondEntered.countDown();
                        active.decrementAndGet();
                        return null;
                    });
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
            assertEquals(1, secondEntered.getCount(), "second operation must wait for the first lock");
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
        assertEquals(1, maximum.get());
        assertTrue(Files.exists(tempDir.resolve(".metadata.json.lock")));
    }

    @Test
    void sidecarLockIsReleasedWhenOperationFails() throws Exception {
        Path sidecar = tempDir.resolve("metadata.json");
        IOException expected = new IOException("forced operation failure");

        IOException thrown = assertThrows(IOException.class,
                () -> MediaMetadataSidecarFiles.withSidecarLock(sidecar, () -> {
                    throw expected;
                }));

        assertSame(expected, thrown);
        assertEquals("completed",
                MediaMetadataSidecarFiles.withSidecarLock(sidecar, () -> "completed"));
    }


    @Test
    void sidecarLockSerializesOperationsAcrossProcesses() throws Exception {
        Path sidecar = tempDir.resolve("cross-process.json");
        Path holderEntered = tempDir.resolve("holder-entered");
        Path releaseHolder = tempDir.resolve("release-holder");
        Path contenderEntered = tempDir.resolve("contender-entered");
        String helperClass = "tech.kayys.aqli.media.metadata.MediaMetadataSidecarFilesTest$SidecarLockProcess";
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        String classpath = System.getProperty("java.class.path");

        Process holder = new ProcessBuilder(java, "-cp", classpath, helperClass,
                "hold", sidecar.toString(), holderEntered.toString(), releaseHolder.toString())
                .redirectErrorStream(true).start();
        Process contender = null;
        try {
            awaitFile(holderEntered, holder, "holder process should acquire the sidecar lock");
            contender = new ProcessBuilder(java, "-cp", classpath, helperClass,
                    "contend", sidecar.toString(), contenderEntered.toString(), "-")
                    .redirectErrorStream(true).start();

            Thread.sleep(300);
            assertFalse(Files.exists(contenderEntered),
                    "a second JVM must wait while the first JVM holds the sidecar lock");

            Files.createFile(releaseHolder);
            assertTrue(holder.waitFor(10, TimeUnit.SECONDS), "holder process should finish");
            assertTrue(contender.waitFor(10, TimeUnit.SECONDS), "contender process should finish");
            assertEquals(0, holder.exitValue());
            assertEquals(0, contender.exitValue());
            assertTrue(Files.exists(contenderEntered));
        } finally {
            Files.createFile(releaseHolder);
            if (holder.isAlive()) {
                holder.destroyForcibly();
            }
            if (contender != null && contender.isAlive()) {
                contender.destroyForcibly();
            }
        }
    }

    private static void awaitFile(Path marker, Process process, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(marker) && System.nanoTime() < deadline && process.isAlive()) {
            Thread.sleep(20);
        }
        assertTrue(Files.exists(marker), message);
    }

    public static final class SidecarLockProcess {
        private SidecarLockProcess() {
        }

        public static void main(String[] args) throws Exception {
            Path sidecar = Path.of(args[1]);
            Path entered = Path.of(args[2]);
            Path release = Path.of(args[3]);
            MediaMetadataSidecarFiles.withSidecarLock(sidecar, () -> {
                Files.createFile(entered);
                if ("hold".equals(args[0])) {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    while (!Files.exists(release) && System.nanoTime() < deadline) {
                        Thread.sleep(20);
                    }
                    if (!Files.exists(release)) {
                        throw new IOException("timed out waiting for parent process to release lock");
                    }
                }
                return null;
            });
        }
    }

    @Test
    void writesUtf8ContentBeforeReplacingDestination() throws Exception {
        Path sidecar = tempDir.resolve("metadata.json");
        String content = "{\"prompt\":\"こんにちは 🌳\"}";

        MediaMetadataSidecarFiles.writeWithAtomicMoveFallback(sidecar, content);

        assertEquals(content, Files.readString(sidecar));
    }
}

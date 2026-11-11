package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptimizedCacheDirectoryScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsMissingCacheDirectoryWithoutCreatingIt() {
        Path cacheRoot = tempDir.resolve("missing");

        OptimizedCacheDirectoryScanner.Stats stats =
                OptimizedCacheDirectoryScanner.scan(cacheRoot, Set.of(), Set.of());

        assertFalse(stats.exists());
        assertEquals(0, stats.fileCount());
        assertFalse(Files.exists(cacheRoot));
    }

    @Test
    void classifiesCurrentStaleAndUnrelatedOptimizedGraphs() throws Exception {
        Path cacheRoot = Files.createDirectory(tempDir.resolve("cache"));
        Path current = Files.writeString(cacheRoot.resolve("tts-prefill-abc.optimized.onnx"), "current");
        Path stale = Files.writeString(cacheRoot.resolve("tts-decode-old.optimized.onnx"), "stale");
        Path unrelated = Files.writeString(cacheRoot.resolve("other-model.optimized.onnx"), "other");
        Files.writeString(cacheRoot.resolve("codec.data"), "sidecar");
        Files.writeString(cacheRoot.resolve("notes.txt"), "ignored by graph classification");

        OptimizedCacheDirectoryScanner.Stats stats =
                OptimizedCacheDirectoryScanner.scan(
                        cacheRoot,
                        Set.of(current.toAbsolutePath().normalize()),
                        Set.of("tts-decode-"));

        assertTrue(stats.exists());
        assertEquals(5, stats.fileCount());
        assertEquals(3, stats.optimizedGraphFiles());
        assertEquals(1, stats.currentOptimizedGraphFiles());
        assertEquals(1, stats.staleOptimizedGraphFiles());
        assertEquals(1, stats.unrelatedOptimizedGraphFiles());
        assertEquals(1, stats.externalDataSidecarFiles());
        assertEquals("codec.data", stats.externalDataSidecarFileNames().get(0));
    }

    @Test
    void marksSidecarsPruneableOnlyWhenNoOptimizedGraphsRemainAfterStaleCleanup() throws Exception {
        Path cacheRoot = Files.createDirectory(tempDir.resolve("cache"));
        Files.writeString(cacheRoot.resolve("tts-prefill-old.optimized.onnx"), "stale");
        Path sidecar = Files.writeString(cacheRoot.resolve("weights.data"), "data");

        OptimizedCacheDirectoryScanner.Stats stats =
                OptimizedCacheDirectoryScanner.scan(cacheRoot, Set.of(), Set.of("tts-prefill-"));

        assertEquals(0, stats.orphanExternalDataSidecarFiles());
        assertEquals(1, stats.pruneableExternalDataSidecarFiles());
        assertEquals(0, stats.retainedExternalDataSidecarFiles());
        assertEquals(1, stats.pruneableExternalDataSidecarPaths().size());
        assertEquals(sidecar.toAbsolutePath().normalize(), stats.pruneableExternalDataSidecarPaths().get(0));
        assertTrue(stats.externalDataSidecarPrunePolicy().contains("no optimized graphs remain"));
    }

    @Test
    void retainsSidecarsWhenAnOptimizedGraphWillRemain() throws Exception {
        Path cacheRoot = Files.createDirectory(tempDir.resolve("cache"));
        Path current = Files.writeString(cacheRoot.resolve("tts-prefill-current.optimized.onnx"), "current");
        Files.writeString(cacheRoot.resolve("weights.data"), "data");

        OptimizedCacheDirectoryScanner.Stats stats =
                OptimizedCacheDirectoryScanner.scan(
                        cacheRoot,
                        Set.of(current.toAbsolutePath().normalize()),
                        Set.of("tts-prefill-"));

        assertEquals(0, stats.pruneableExternalDataSidecarFiles());
        assertEquals(1, stats.retainedExternalDataSidecarFiles());
        assertTrue(stats.externalDataSidecarPrunePolicy().contains("keep sidecars"));
    }
}

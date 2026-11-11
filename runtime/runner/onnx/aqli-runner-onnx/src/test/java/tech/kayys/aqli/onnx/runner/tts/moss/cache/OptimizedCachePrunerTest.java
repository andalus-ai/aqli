package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptimizedCachePrunerTest {
    @TempDir
    Path tempDir;

    @Test
    void dryRunReportsStaleGraphsAndEligibleSidecarsWithoutDeletingThem() throws Exception {
        Path cache = Files.createDirectory(tempDir.resolve("cache"));
        Path staleGraph = Files.writeString(cache.resolve("tts-old.optimized.onnx"), "graph");
        Path sidecar = Files.writeString(cache.resolve("weights.data"), "data");
        var inventory = OptimizedCacheDirectoryScanner.scan(cache, Set.of(), Set.of("tts-"));

        var operations = OptimizedCachePruner.prune(inventory, true);

        assertEquals(2, operations.size());
        assertTrue(operations.stream().allMatch(OptimizedCachePruner.Operation::dryRun));
        assertTrue(operations.stream().allMatch(operation -> !operation.deleted()));
        assertTrue(Files.exists(staleGraph));
        assertTrue(Files.exists(sidecar));
        assertEquals("would_delete", operations.get(0).toJson().get("action"));
    }

    @Test
    void applyDeletesStaleGraphsAndSidecarsMadeEligibleByStaleCleanup() throws Exception {
        Path cache = Files.createDirectory(tempDir.resolve("cache"));
        Path staleGraph = Files.writeString(cache.resolve("tts-old.optimized.onnx"), "graph");
        Path sidecar = Files.writeString(cache.resolve("weights.data"), "data");
        var inventory = OptimizedCacheDirectoryScanner.scan(cache, Set.of(), Set.of("tts-"));

        var operations = OptimizedCachePruner.prune(inventory, false);

        assertEquals(2, operations.size());
        assertTrue(operations.stream().allMatch(OptimizedCachePruner.Operation::deleted));
        assertFalse(Files.exists(staleGraph));
        assertFalse(Files.exists(sidecar));
    }

    @Test
    void applyRetainsSidecarsWhileAnOptimizedGraphWillRemain() throws Exception {
        Path cache = Files.createDirectory(tempDir.resolve("cache"));
        Path currentGraph = Files.writeString(cache.resolve("tts-current.optimized.onnx"), "graph");
        Path sidecar = Files.writeString(cache.resolve("weights.data"), "data");
        var inventory = OptimizedCacheDirectoryScanner.scan(
                cache,
                Set.of(currentGraph.toAbsolutePath().normalize()),
                Set.of("tts-"));

        var operations = OptimizedCachePruner.prune(inventory, false);

        assertTrue(operations.isEmpty());
        assertTrue(Files.exists(currentGraph));
        assertTrue(Files.exists(sidecar));
    }

    @Test
    void applyDeletesStaleGraphsAndSidecarsInsideASourceScopedDirectory() throws Exception {
        Path cache = Files.createDirectory(tempDir.resolve("cache"));
        Path sourceScope = Files.createDirectory(cache.resolve("source-model"));
        Path staleGraph = Files.writeString(sourceScope.resolve("tts-old.optimized.onnx"), "graph");
        Path sidecar = Files.writeString(sourceScope.resolve("weights.data"), "data");
        var inventory = OptimizedCacheDirectoryScanner.scan(cache, Set.of(), Set.of("tts-"));

        var operations = OptimizedCachePruner.prune(inventory, false);

        assertEquals(2, operations.size());
        assertTrue(operations.stream().allMatch(OptimizedCachePruner.Operation::deleted));
        assertFalse(Files.exists(staleGraph));
        assertFalse(Files.exists(sidecar));
    }

    @Test
    void refusesInventoryEntriesOutsideTheScannedCacheRoot() throws Exception {
        Path cache = Files.createDirectory(tempDir.resolve("cache"));
        Path outside = Files.writeString(tempDir.resolve("important.optimized.onnx"), "keep");
        var scanned = OptimizedCacheDirectoryScanner.scan(cache, Set.of(), Set.of());
        var forged = withStalePaths(scanned, List.of(outside));

        var operations = OptimizedCachePruner.prune(forged, false);

        assertEquals(1, operations.size());
        assertFalse(operations.getFirst().deleted());
        assertNotNull(operations.getFirst().error());
        assertTrue(Files.exists(outside));
    }

    @Test
    void refusesAStalePathThatHasBeenReplacedByADirectory() throws Exception {
        Path cache = Files.createDirectory(tempDir.resolve("cache"));
        Path staleGraph = Files.writeString(cache.resolve("tts-old.optimized.onnx"), "graph");
        var inventory = OptimizedCacheDirectoryScanner.scan(cache, Set.of(), Set.of("tts-"));
        Files.delete(staleGraph);
        Files.createDirectory(staleGraph);

        var operations = OptimizedCachePruner.prune(inventory, false);

        assertEquals(1, operations.size());
        assertFalse(operations.getFirst().deleted());
        assertNotNull(operations.getFirst().error());
        assertTrue(Files.isDirectory(staleGraph));
    }

    private static OptimizedCacheDirectoryScanner.Stats withStalePaths(
            OptimizedCacheDirectoryScanner.Stats stats, List<Path> stalePaths) {
        return new OptimizedCacheDirectoryScanner.Stats(
                stats.path(),
                stats.exists(),
                stats.fileCount(),
                stats.optimizedGraphFiles(),
                stats.currentOptimizedGraphFiles(),
                stats.staleOptimizedGraphFiles(),
                stats.unrelatedOptimizedGraphFiles(),
                stats.externalDataSidecarFiles(),
                stats.sizeBytes(),
                stats.staleOptimizedGraphSizeBytes(),
                stats.unrelatedOptimizedGraphSizeBytes(),
                stats.externalDataSidecarSizeBytes(),
                stats.externalDataSidecarPaths(),
                stats.orphanExternalDataSidecarPaths(),
                stats.unrelatedOptimizedGraphPaths(),
                stalePaths);
    }
}

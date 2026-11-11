package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
}

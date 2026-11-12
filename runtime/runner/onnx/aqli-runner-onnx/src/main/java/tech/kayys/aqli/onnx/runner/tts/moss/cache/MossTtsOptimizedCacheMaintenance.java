package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import tech.kayys.aqli.onnx.runner.tts.moss.MossTtsOnnxGraphResolver;

/**
 * Coordinates inventory, dry-run, and pruning for one local MOSS TTS model's
 * optimized ONNX graph cache.
 *
 * <p>The CLI owns model selection and presentation. This component owns the
 * MOSS graph-to-cache scope and the cache maintenance operation.</p>
 */
public final class MossTtsOptimizedCacheMaintenance {

    public Result prune(Path modelDir, Path codecDir, Path cacheRoot, boolean dryRun) throws Exception {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(codecDir, "codecDir");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        return OptimizedModelCacheLock.withLock(cacheRoot, () -> pruneLocked(modelDir, codecDir, cacheRoot, dryRun));
    }

    private Result pruneLocked(Path modelDir, Path codecDir, Path cacheRoot, boolean dryRun) throws Exception {
        OptimizedModelCacheFiles.cleanupAbandonedStages(cacheRoot);
        Set<Path> currentPaths = new HashSet<>();
        Set<String> currentPrefixes = new HashSet<>();
        for (MossTtsOnnxGraphResolver.Graph graph :
                MossTtsOnnxGraphResolver.resolveGraphs(modelDir, codecDir)) {
            currentPaths.add(MossTtsOnnxGraphResolver.optimizedCachePath(graph.path(), cacheRoot)
                    .toAbsolutePath().normalize());
            currentPrefixes.add(MossTtsOnnxGraphResolver.cacheFilenamePrefix(graph.path()));
            currentPrefixes.add(MossTtsOnnxGraphResolver.cacheEntryPrefix(graph.path(), cacheRoot));
        }

        OptimizedCacheDirectoryScanner.Stats before =
                OptimizedCacheDirectoryScanner.scan(cacheRoot, currentPaths, currentPrefixes);
        List<OptimizedCachePruner.Operation> operations = OptimizedCachePruner.prune(before, dryRun);

        int errors = 0;
        long affectedBytes = 0L;
        for (OptimizedCachePruner.Operation operation : operations) {
            if (dryRun) {
                affectedBytes += operation.sizeBytes();
            } else if (operation.error() != null && !operation.error().isBlank()) {
                errors++;
            } else if (operation.deleted()) {
                affectedBytes += operation.sizeBytes();
            }
        }

        OptimizedCacheDirectoryScanner.Stats after = dryRun
                ? before
                : OptimizedCacheDirectoryScanner.scan(cacheRoot, currentPaths, currentPrefixes);
        return new Result(before, after, operations, affectedBytes, errors);
    }

    public record Result(
            OptimizedCacheDirectoryScanner.Stats before,
            OptimizedCacheDirectoryScanner.Stats after,
            List<OptimizedCachePruner.Operation> operations,
            long affectedBytes,
            int errors) {
        public Result {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            operations = List.copyOf(operations);
        }
    }
}

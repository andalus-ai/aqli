package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Collects one consistent snapshot of MOSS TTS optimized ONNX cache diagnostics.
 * Presentation and output-format decisions remain with the CLI command.
 */
final class MossTtsOnnxCacheDiagnostics {
    private MossTtsOnnxCacheDiagnostics() {
    }

    static Snapshot inspect(Path modelDir, Optional<Path> codecDir, MossTtsCacheSettings settings) {
        Path cacheRoot = settings.optimizedGraphCacheRoot();
        Set<Path> currentPaths = new HashSet<>();
        Set<String> currentPrefixes = new HashSet<>();
        List<GraphStatus> graphStatuses = new ArrayList<>();
        boolean graphsAvailable = false;
        String graphError = null;

        if (codecDir.isPresent()) {
            try {
                List<MossTtsOnnxGraphResolver.Graph> graphs =
                        MossTtsOnnxGraphResolver.resolveGraphs(modelDir, codecDir.get());
                for (MossTtsOnnxGraphResolver.Graph graph : graphs) {
                    Path optimizedPath = MossTtsOnnxGraphResolver.optimizedCachePath(graph.path(), cacheRoot);
                    currentPaths.add(optimizedPath.toAbsolutePath().normalize());
                    currentPrefixes.add(MossTtsOnnxGraphResolver.cacheFilenamePrefix(graph.path()));
                    graphStatuses.add(new GraphStatus(
                            graph,
                            optimizedPath,
                            Files.isRegularFile(optimizedPath),
                            MossTtsOnnxGraphResolver.externalDataSidecars(graph.path()).size()));
                }
                graphsAvailable = true;
            } catch (Exception e) {
                graphStatuses.clear();
                graphError = e.getMessage();
            }
        }

        OptimizedCacheDirectoryScanner.Stats directory =
                OptimizedCacheDirectoryScanner.scan(cacheRoot, currentPaths, currentPrefixes);
        return new Snapshot(settings, graphsAvailable, graphError, List.copyOf(graphStatuses), directory);
    }

    record GraphStatus(
            MossTtsOnnxGraphResolver.Graph graph,
            Path optimizedPath,
            boolean warm,
            int externalDataSidecars) {
    }

    record Snapshot(
            MossTtsCacheSettings settings,
            boolean graphsAvailable,
            String graphError,
            List<GraphStatus> graphs,
            OptimizedCacheDirectoryScanner.Stats cacheDirectory) {
        int warmGraphCount() {
            return (int) graphs.stream().filter(GraphStatus::warm).count();
        }
    }
}

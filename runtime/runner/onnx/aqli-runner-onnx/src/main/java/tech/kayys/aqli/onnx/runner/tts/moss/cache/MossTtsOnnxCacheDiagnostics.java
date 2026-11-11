package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import tech.kayys.aqli.onnx.runner.tts.moss.MossTtsOnnxGraphResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Collects one consistent snapshot of MOSS TTS optimized ONNX cache diagnostics.
 * Presentation and output-format decisions remain with the CLI command.
 */
public final class MossTtsOnnxCacheDiagnostics {
    private MossTtsOnnxCacheDiagnostics() {
    }

    public static Snapshot inspect(Path modelDir, Optional<Path> codecDir, MossTtsCacheSettings settings) {
        Path cacheRoot = settings.optimizedGraphCacheRoot();
        Set<Path> currentPaths = new HashSet<>();
        Set<String> currentPrefixes = new HashSet<>();
        List<GraphStatus> graphStatuses = new ArrayList<>();
        Map<Path, List<Path>> sidecarsBySourceDirectory = new HashMap<>();
        boolean graphsAvailable = false;
        String graphError = null;

        if (codecDir.isPresent()) {
            try {
                List<MossTtsOnnxGraphResolver.Graph> graphs =
                        MossTtsOnnxGraphResolver.resolveGraphs(modelDir, codecDir.get());
                for (MossTtsOnnxGraphResolver.Graph graph : graphs) {
                    List<Path> sidecars = sidecarsFor(graph.path(), sidecarsBySourceDirectory);
                    Path optimizedPath =
                            MossTtsOnnxGraphResolver.optimizedCachePath(graph.path(), cacheRoot, sidecars);
                    currentPaths.add(optimizedPath.toAbsolutePath().normalize());
                    currentPrefixes.add(MossTtsOnnxGraphResolver.cacheFilenamePrefix(graph.path()));
                    graphStatuses.add(new GraphStatus(
                            graph, optimizedPath, Files.isRegularFile(optimizedPath), sidecars.size()));
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

    private static List<Path> sidecarsFor(Path modelPath, Map<Path, List<Path>> bySourceDirectory)
            throws Exception {
        Path sourceDirectory = modelPath.toAbsolutePath().normalize().getParent();
        if (sourceDirectory == null) {
            return List.of();
        }
        List<Path> cached = bySourceDirectory.get(sourceDirectory);
        if (cached != null) {
            return cached;
        }
        List<Path> discovered = List.copyOf(MossTtsOnnxGraphResolver.externalDataSidecars(modelPath));
        bySourceDirectory.put(sourceDirectory, discovered);
        return discovered;
    }

    public record GraphStatus(
            MossTtsOnnxGraphResolver.Graph graph,
            Path optimizedPath,
            boolean warm,
            int externalDataSidecars) {
    }

    public record Snapshot(
            MossTtsCacheSettings settings,
            boolean graphsAvailable,
            String graphError,
            List<GraphStatus> graphs,
            OptimizedCacheDirectoryScanner.Stats cacheDirectory) {
        public int warmGraphCount() {
            return (int) graphs.stream().filter(GraphStatus::warm).count();
        }
    }
}

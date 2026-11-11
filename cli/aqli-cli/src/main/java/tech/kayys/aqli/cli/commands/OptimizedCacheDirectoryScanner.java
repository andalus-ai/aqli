package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans the MOSS TTS optimized-graph cache and classifies its contents
 * against the optimized graph paths belonging to the current model.
 */
final class OptimizedCacheDirectoryScanner {
    private OptimizedCacheDirectoryScanner() {
    }

    static Stats scan(
            Path cacheRoot,
            Set<Path> currentOptimizedPaths,
            Set<String> currentPrefixes) {
        Path root = cacheRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new Stats(
                    root,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    0L,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }
                int fileCount = 0;
        int optimizedGraphFiles = 0;
        int currentOptimizedGraphFiles = 0;
        int staleOptimizedGraphFiles = 0;
        int unrelatedOptimizedGraphFiles = 0;
        int externalDataSidecarFiles = 0;
        long sizeBytes = 0L;
        long staleBytes = 0L;
        long unrelatedBytes = 0L;
        long externalDataSidecarBytes = 0L;
        List<Path> externalDataSidecarPaths = new ArrayList<>();
        List<Path> stalePaths = new ArrayList<>();
        List<Path> unrelatedPaths = new ArrayList<>();
        try (var stream = Files.list(root)) {
            for (Path file : stream.toList()) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                fileCount++;
                try {
                    sizeBytes += Files.size(file);
                } catch (Exception ignored) {
                }
                String filename = file.getFileName().toString();
                if (filename.endsWith(".data")) {
                    externalDataSidecarFiles++;
                    externalDataSidecarBytes += safeSize(file);
                    externalDataSidecarPaths.add(file.toAbsolutePath().normalize());
                }
                if (!filename.endsWith(".optimized.onnx")) {
                    continue;
                }
                optimizedGraphFiles++;
                Path normalized = file.toAbsolutePath().normalize();
                if (currentOptimizedPaths.contains(normalized)) {
                    currentOptimizedGraphFiles++;
                } else if (hasOptimizedCachePrefix(filename, currentPrefixes)) {
                    staleOptimizedGraphFiles++;
                    staleBytes += safeSize(file);
                    stalePaths.add(file.toAbsolutePath().normalize());
                } else {
                    unrelatedOptimizedGraphFiles++;
                    unrelatedBytes += safeSize(file);
                    unrelatedPaths.add(file.toAbsolutePath().normalize());
                }
            }
        } catch (Exception ignored) {
        }
        stalePaths.sort((left, right) -> left.getFileName().toString().compareTo(right.getFileName().toString()));
        unrelatedPaths.sort((left, right) -> left.getFileName().toString().compareTo(right.getFileName().toString()));
        externalDataSidecarPaths.sort((left, right) -> left.getFileName().toString()
                .compareTo(right.getFileName().toString()));
        List<Path> orphanSidecars = optimizedGraphFiles == 0 ? externalDataSidecarPaths : List.of();
        return new OptimizedCacheDirectoryStats(
                root,
                true,
                fileCount,
                optimizedGraphFiles,
                currentOptimizedGraphFiles,
                staleOptimizedGraphFiles,
                unrelatedOptimizedGraphFiles,
                externalDataSidecarFiles,
                sizeBytes,
                staleBytes,
                unrelatedBytes,
                externalDataSidecarBytes,
                externalDataSidecarPaths,
                orphanSidecars,
                unrelatedPaths,
                stalePaths);
    }

    private static boolean hasOptimizedCachePrefix(String filename, Set<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (filename.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    record Stats(
            Path path,
            boolean exists,
            int fileCount,
            int optimizedGraphFiles,
            int currentOptimizedGraphFiles,
            int staleOptimizedGraphFiles,
            int unrelatedOptimizedGraphFiles,
            int externalDataSidecarFiles,
            long sizeBytes,
            long staleOptimizedGraphSizeBytes,
            long unrelatedOptimizedGraphSizeBytes,
            long externalDataSidecarSizeBytes,
            List<Path> externalDataSidecarPaths,
            List<Path> orphanExternalDataSidecarPaths,
            List<Path> unrelatedOptimizedGraphPaths,
            List<Path> staleOptimizedGraphPaths) {
        Map<String, Object> toJson() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("path", path.toString());
            value.put("exists", exists);
            value.put("file_count", fileCount);
            value.put("size_bytes", sizeBytes);
            value.put("optimized_graph_files", optimizedGraphFiles);
            value.put("current_optimized_graph_files", currentOptimizedGraphFiles);
            value.put("stale_optimized_graph_files", staleOptimizedGraphFiles);
            value.put("stale_optimized_graph_size_bytes", staleOptimizedGraphSizeBytes);
            value.put("unrelated_optimized_graph_files", unrelatedOptimizedGraphFiles);
            value.put("unrelated_optimized_graph_size_bytes", unrelatedOptimizedGraphSizeBytes);
            value.put("external_data_sidecar_files", externalDataSidecarFiles);
            value.put("external_data_sidecar_size_bytes", externalDataSidecarSizeBytes);
            value.put("external_data_sidecar_prune_policy", externalDataSidecarPrunePolicy());
            value.put("optimized_graph_files_after_stale_prune", optimizedGraphFilesAfterStalePrune());
            value.put("pruneable_external_data_sidecar_files", pruneableExternalDataSidecarFiles());
            value.put("pruneable_external_data_sidecar_size_bytes", pruneableExternalDataSidecarSizeBytes());
            value.put("pruneable_external_data_sidecar_file_names", pruneableExternalDataSidecarFileNames());
            value.put("pruneable_external_data_sidecar_paths", pruneableExternalDataSidecarPaths().stream()
                    .map(Path::toString)
                    .toList());
            value.put("retained_external_data_sidecar_files", retainedExternalDataSidecarFiles());
            value.put("retained_external_data_sidecar_size_bytes", retainedExternalDataSidecarSizeBytes());
            value.put("retained_external_data_sidecar_file_names", retainedExternalDataSidecarFileNames());
            value.put("retained_external_data_sidecar_paths", retainedExternalDataSidecarPaths().stream()
                    .map(Path::toString)
                    .toList());
            value.put("external_data_sidecar_file_names", externalDataSidecarFileNames());
            value.put("external_data_sidecar_paths", externalDataSidecarPaths.stream()
                    .map(Path::toString)
                    .toList());
            value.put("orphan_external_data_sidecar_files", orphanExternalDataSidecarFiles());
            value.put("orphan_external_data_sidecar_size_bytes", orphanExternalDataSidecarSizeBytes());
            value.put("orphan_external_data_sidecar_file_names", orphanExternalDataSidecarFileNames());
            value.put("orphan_external_data_sidecar_paths", orphanExternalDataSidecarPaths.stream()
                    .map(Path::toString)
                    .toList());
            value.put("stale_optimized_graph_file_names", staleOptimizedGraphFileNames());
            value.put("stale_optimized_graph_paths", staleOptimizedGraphPaths.stream()
                    .map(Path::toString)
                    .toList());
            value.put("unrelated_optimized_graph_file_names", unrelatedOptimizedGraphFileNames());
            value.put("unrelated_optimized_graph_paths", unrelatedOptimizedGraphPaths.stream()
                    .map(Path::toString)
                    .toList());
            return value;
        }

        List<String> staleOptimizedGraphFileNames() {
            return staleOptimizedGraphPaths.stream()
                    .map(path -> path.getFileName().toString())
                    .toList();
        }

        List<String> unrelatedOptimizedGraphFileNames() {
            return unrelatedOptimizedGraphPaths.stream()
                    .map(path -> path.getFileName().toString())
                    .toList();
        }

        int orphanExternalDataSidecarFiles() {
            return orphanExternalDataSidecarPaths.size();
        }

        long orphanExternalDataSidecarSizeBytes() {
            long total = 0L;
            for (Path path : orphanExternalDataSidecarPaths) {
                total += safeSize(path);
            }
            return total;
        }

        List<String> externalDataSidecarFileNames() {
            return externalDataSidecarPaths.stream()
                    .map(path -> path.getFileName().toString())
                    .toList();
        }

        List<String> orphanExternalDataSidecarFileNames() {
            return orphanExternalDataSidecarPaths.stream()
                    .map(path -> path.getFileName().toString())
                    .toList();
        }

        int optimizedGraphFilesAfterStalePrune() {
            return Math.max(0, optimizedGraphFiles - staleOptimizedGraphFiles);
        }

        List<Path> pruneableExternalDataSidecarPaths() {
            if (externalDataSidecarPaths.isEmpty()) {
                return List.of();
            }
            return optimizedGraphFilesAfterStalePrune() == 0 ? externalDataSidecarPaths : List.of();
        }

        int pruneableExternalDataSidecarFiles() {
            return pruneableExternalDataSidecarPaths().size();
        }

        long pruneableExternalDataSidecarSizeBytes() {
            long total = 0L;
            for (Path path : pruneableExternalDataSidecarPaths()) {
                total += safeSize(path);
            }
            return total;
        }

        List<String> pruneableExternalDataSidecarFileNames() {
            return pruneableExternalDataSidecarPaths().stream()
                    .map(path -> path.getFileName().toString())
                    .toList();
        }

        List<Path> retainedExternalDataSidecarPaths() {
            if (externalDataSidecarPaths.isEmpty()) {
                return List.of();
            }
            List<Path> pruneable = pruneableExternalDataSidecarPaths();
            if (pruneable.isEmpty()) {
                return externalDataSidecarPaths;
            }
            if (pruneable.size() == externalDataSidecarPaths.size()) {
                return List.of();
            }
            Set<Path> pruneableSet = new java.util.HashSet<>(pruneable);
            return externalDataSidecarPaths.stream()
                    .filter(path -> !pruneableSet.contains(path))
                    .toList();
        }

        int retainedExternalDataSidecarFiles() {
            return retainedExternalDataSidecarPaths().size();
        }

        long retainedExternalDataSidecarSizeBytes() {
            long total = 0L;
            for (Path path : retainedExternalDataSidecarPaths()) {
                total += safeSize(path);
            }
            return total;
        }

        List<String> retainedExternalDataSidecarFileNames() {
            return retainedExternalDataSidecarPaths().stream()
                    .map(path -> path.getFileName().toString())
                    .toList();
        }

        String externalDataSidecarPrunePolicy() {
            if (externalDataSidecarPaths.isEmpty()) {
                return "none";
            }
            if (optimizedGraphFiles == 0) {
                return "prune orphan sidecars; no optimized graphs are present";
            }
            if (optimizedGraphFilesAfterStalePrune() == 0) {
                return "prune sidecars with stale graphs; no optimized graphs remain after stale cleanup";
            }
            return "keep sidecars; optimized graphs remain after stale cleanup";
        }
    }
}


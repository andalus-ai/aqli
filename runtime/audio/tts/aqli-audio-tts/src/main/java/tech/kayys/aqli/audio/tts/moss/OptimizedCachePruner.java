package tech.kayys.aqli.audio.tts.moss;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the MOSS TTS optimized-cache prune policy to a scanned cache inventory.
 *
 * <p>The scanner determines which files are stale and which external-data sidecars
 * are safe to remove. This component performs the requested dry-run or deletion
 * and captures per-file outcomes; CLI presentation remains with the command.</p>
 */
public final class OptimizedCachePruner {
    private OptimizedCachePruner() {
    }

    public static List<Operation> prune(OptimizedCacheDirectoryScanner.Stats before, boolean dryRun) {
        List<Operation> operations = new ArrayList<>();
        for (Path path : before.staleOptimizedGraphPaths()) {
            operations.add(pruneFile("optimized_graph", path, dryRun));
        }
        for (Path path : before.pruneableExternalDataSidecarPaths()) {
            operations.add(pruneFile("external_data_sidecar", path, dryRun));
        }
        return List.copyOf(operations);
    }

    private static Operation pruneFile(String kind, Path path, boolean dryRun) {
        long sizeBytes = OptimizedCacheDirectoryScanner.safeSize(path);
        if (dryRun) {
            return new Operation(kind, path, sizeBytes, false, true, null);
        }
        try {
            boolean deleted = Files.deleteIfExists(path);
            return new Operation(kind, path, sizeBytes, deleted, false, null);
        } catch (Exception e) {
            return new Operation(kind, path, sizeBytes, false, false, e.getMessage());
        }
    }

    public record Operation(
            String kind,
            Path path,
            long sizeBytes,
            boolean deleted,
            boolean dryRun,
            String error) {
        public Map<String, Object> toJson() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("kind", kind);
            value.put("path", path.toString());
            value.put("file", path.getFileName().toString());
            value.put("size_bytes", sizeBytes);
            value.put("dry_run", dryRun);
            value.put("deleted", deleted);
            value.put("action", dryRun ? "would_delete" : (deleted ? "deleted" : (error == null ? "missing" : "failed")));
            if (error != null && !error.isBlank()) {
                value.put("error", error);
            }
            return value;
        }
    }
}

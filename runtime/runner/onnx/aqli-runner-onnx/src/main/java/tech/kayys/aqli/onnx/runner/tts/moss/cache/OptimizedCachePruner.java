package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import java.nio.file.Files;
import java.nio.file.LinkOption;
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
        Path cacheRoot = before.path().toAbsolutePath().normalize();
        for (Path path : before.staleOptimizedGraphPaths()) {
            operations.add(pruneFile("optimized_graph", cacheRoot, path, dryRun));
        }
        for (Path path : before.pruneableExternalDataSidecarPaths()) {
            operations.add(pruneFile("external_data_sidecar", cacheRoot, path, dryRun));
        }
        return List.copyOf(operations);
    }

    private static Operation pruneFile(String kind, Path cacheRoot, Path path, boolean dryRun) {
        Path candidate = path.toAbsolutePath().normalize();
        String requiredSuffix = switch (kind) {
            case "optimized_graph" -> ".optimized.onnx";
            case "external_data_sidecar" -> ".data";
            default -> null;
        };
        Path relative = cacheRoot.relativize(candidate);
        boolean supportedDepth = relative.getNameCount() == 1 || relative.getNameCount() == 2;
        boolean supportedParent = relative.getNameCount() == 1
                || (relative.getNameCount() == 2
                        && Files.isDirectory(candidate.getParent(), LinkOption.NOFOLLOW_LINKS));
        if (requiredSuffix == null
                || !candidate.startsWith(cacheRoot)
                || !supportedDepth
                || !supportedParent
                || !candidate.getFileName().toString().endsWith(requiredSuffix)) {
            return refused(kind, candidate, "refused to prune a path outside the cache inventory");
        }
        // Do not follow links: a stale inventory must never turn a symlink target
        // or a replacement directory into a deletion target.
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return refused(kind, candidate, "refused to prune a path that is not a regular file");
        }

        long sizeBytes = OptimizedCacheDirectoryScanner.safeSize(candidate);
        if (dryRun) {
            return new Operation(kind, candidate, sizeBytes, false, true, null);
        }
        try {
            boolean deleted = Files.deleteIfExists(candidate);
            return new Operation(kind, candidate, sizeBytes, deleted, false, null);
        } catch (Exception e) {
            return new Operation(kind, candidate, sizeBytes, false, false, e.getMessage());
        }
    }

    private static Operation refused(String kind, Path path, String reason) {
        return new Operation(kind, path, 0L, false, false, reason);
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

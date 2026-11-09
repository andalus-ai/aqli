package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Selects a runnable local artifact for a model provider.
 *
 * <p>This resolver currently owns the LibTorch/TorchScript artifact compatibility policy.
 * It does not load models or execute inference; the CLI remains responsible for provider
 * routing and user-facing diagnostics.</p>
 */
final class ProviderArtifactResolver {

    record Artifact(String modelId, String shortId, String path, String format) {
    }

    private ProviderArtifactResolver() {
    }

    static Optional<Path> findLibtorchArtifact(
            List<Artifact> artifacts,
            String requestedModelId,
            String localPath) {
        if (artifacts == null || artifacts.isEmpty()) {
            return Optional.empty();
        }

        String canonicalModelId = artifacts.stream()
                .filter(artifact -> artifact != null
                        && requestedModelId != null
                        && (requestedModelId.equalsIgnoreCase(artifact.modelId())
                        || requestedModelId.equalsIgnoreCase(artifact.shortId())))
                .map(Artifact::modelId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElseGet(() -> artifacts.stream()
                        .filter(artifact -> artifact != null)
                        .filter(artifact -> artifact.path() != null
                                && artifact.path().equals(localPath))
                        .map(Artifact::modelId)
                        .filter(id -> id != null && !id.isBlank())
                        .findFirst()
                        .orElse(requestedModelId));

        return artifacts.stream()
                .filter(artifact -> artifact != null
                        && canonicalModelId != null
                        && canonicalModelId.equalsIgnoreCase(artifact.modelId()))
                .filter(artifact -> artifact.path() != null
                        && !artifact.path().isBlank()
                        && !artifact.path().equals(localPath))
                .filter(artifact -> resolveLibtorchArtifact(safePath(artifact.path())).isPresent())
                .sorted(Comparator.comparingInt(
                        (Artifact artifact) -> libtorchArtifactPriority(artifact.path(), artifact.format()))
                        .reversed())
                .map(artifact -> resolveLibtorchArtifact(safePath(artifact.path())))
                .flatMap(Optional::stream)
                .findFirst();
    }

    static Optional<Path> resolveLibtorchArtifact(Path root) {
        if (root == null || !Files.exists(root)) {
            return Optional.empty();
        }
        if (Files.isRegularFile(root) && isLibtorchModelFile(root)) {
            return Optional.of(root);
        }
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.walk(root, 3)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(ProviderArtifactResolver::isLibtorchModelFile)
                    .sorted(Comparator.comparingInt(
                            (Path path) -> libtorchArtifactPriority(path, null)).reversed())
                    .findFirst();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Path safePath(String value) {
        try {
            return Path.of(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isLibtorchModelFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".pt")
                || name.endsWith(".pts")
                || name.endsWith(".pth")
                || name.endsWith(".bin");
    }

    private static int libtorchArtifactPriority(Path path, String format) {
        return path == null ? 0 : libtorchArtifactPriority(path.toString(), format);
    }

    private static int libtorchArtifactPriority(String path, String format) {
        int score = 0;
        String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        String normalizedFormat = format == null ? "" : format.toLowerCase(Locale.ROOT);
        if (normalizedFormat.equals("torchscript")) {
            score += 50;
        } else if (normalizedFormat.equals("pytorch")) {
            score += 20;
        }
        if (normalizedPath.contains("/libtorchscript/")) {
            score += 40;
        }
        if (normalizedPath.endsWith(".pt")) {
            score += 30;
        } else if (normalizedPath.endsWith(".pts")) {
            score += 25;
        } else if (normalizedPath.endsWith(".pth")) {
            score += 15;
        } else if (normalizedPath.endsWith(".bin")) {
            score += 10;
        }
        return score;
    }
}

package tech.kayys.aqli.provider.litert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves a LiteRT-LM artifact from explicit paths or the local model index.
 *
 * <p>Callers own index location and equivalent-artifact policy. This class
 * owns only LiteRT route selection and the selection of a runnable
 * {@code .litertlm} artifact from a file or model directory.</p>
 */
public final class LiteRTRouteResolver {
    private final LiteRTModelIndexResolver indexResolver;

    public LiteRTRouteResolver() {
        this(new LiteRTModelIndexResolver());
    }

    LiteRTRouteResolver(LiteRTModelIndexResolver indexResolver) {
        this.indexResolver = indexResolver;
    }

    public record RouteMatch(Path path, String source, boolean cacheHit) {}

    /**
     * Resolves {@code modelFile} first, then a direct {@code model} path, and
     * finally the supplied model index reference.
     */
    public Optional<RouteMatch> resolve(
            String modelFile,
            String model,
            Path indexPath,
            boolean allowEquivalent) {
        Optional<RouteMatch> explicitFile = resolveExplicit(modelFile, "modelFile");
        if (explicitFile.isPresent()) {
            return explicitFile;
        }

        Optional<RouteMatch> directModel = resolveExplicit(model, "model");
        if (directModel.isPresent()) {
            return directModel;
        }

        if (model == null || model.isBlank()) {
            return Optional.empty();
        }
        return indexResolver.resolve(indexPath, model, allowEquivalent)
                .flatMap(indexed -> resolveArtifact(indexed.path())
                        .map(path -> new RouteMatch(path, indexed.source(), true)));
    }

    public Optional<Path> resolveArtifact(Path path) {
        return selectArtifact(path);
    }

    private Optional<RouteMatch> resolveExplicit(String value, String source) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            Path candidate = Path.of(value);
            Path normalized = candidate.isAbsolute()
                    ? candidate.toAbsolutePath().normalize()
                    : Path.of("").toAbsolutePath().resolve(candidate).normalize();
            if (!Files.exists(normalized)) {
                return Optional.empty();
            }
            return resolveArtifact(normalized)
                    .map(path -> new RouteMatch(path, source, false));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Path> selectArtifact(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return isLiteRtLm(path) ? Optional.of(path.toAbsolutePath().normalize()) : Optional.empty();
            }
            Path searchDirectory = Files.isDirectory(path) ? path : path.getParent();
            if (searchDirectory == null || !Files.isDirectory(searchDirectory)) {
                return Optional.empty();
            }
            try (var entries = Files.list(searchDirectory)) {
                return entries.filter(Files::isRegularFile)
                        .filter(LiteRTRouteResolver::isLiteRtLm)
                        .sorted(Comparator.comparingInt(LiteRTRouteResolver::preference))
                        .findFirst()
                        .map(candidate -> candidate.toAbsolutePath().normalize());
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static boolean isLiteRtLm(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".litertlm");
    }

    private static int preference(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int score = 0;
        if (name.contains("qualcomm")) score += 100;
        if (!name.contains("gemma-4-e2b-it.litertlm")) score += 10;
        return score;
    }
}

package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import tech.kayys.aljabr.spi.model.ModelInfo;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Resolves a provider-compatible local artifact before runtime route selection.
 *
 * <p>This is CLI-side compatibility preparation. It does not select a runner or
 * change the runtime's provider-routing policy.</p>
 */
public final class ProviderLocalPathResolver {

    private ProviderLocalPathResolver() {
    }

    public record Result(boolean ok, String localPath) {
    }

    public static Result resolve(
            AqliRuntime runtime,
            String currentProvider,
            String requestedModelId,
            String localPath,
            Predicate<String> safetensorCheckpoint,
            Predicate<String> safetensorWeightFile,
            Consumer<String> standardOutput,
            Consumer<String> standardError) {
        if (localPath == null || localPath.isBlank()
                || currentProvider == null || currentProvider.isBlank()) {
            return new Result(true, localPath);
        }

        String normalizedProvider = ModelFormatResolver.normalizeProvider(currentProvider);
        if (!"libtorch".equals(normalizedProvider)) {
            return new Result(true, localPath);
        }

        try {
            Path originalPath = Path.of(localPath).toAbsolutePath().normalize();
            Optional<Path> directArtifact = ProviderArtifactResolver.resolveLibtorchArtifact(originalPath);
            if (directArtifact.isPresent()) {
                Path resolved = directArtifact.get().toAbsolutePath().normalize();
                if (!resolved.equals(originalPath)) {
                    standardOutput.accept("Provider 'libtorch' selected; using local TorchScript artifact: " + resolved);
                }
                return new Result(true, resolved.toString());
            }
        } catch (Exception ignored) {
            // Fall through to registry-based lookup and the compatibility hint.
        }

        Optional<Path> alternative;
        try {
            alternative = ProviderArtifactResolver.findLibtorchArtifact(
                    runtime.listModels().stream()
                            .map(ProviderLocalPathResolver::artifact)
                            .toList(),
                    requestedModelId,
                    localPath);
        } catch (Exception ignored) {
            alternative = Optional.empty();
        }
        if (alternative.isPresent()) {
            Path artifactPath = alternative.get().toAbsolutePath().normalize();
            standardOutput.accept("Provider 'libtorch' selected; rerouting to registered TorchScript artifact: "
                    + artifactPath);
            return new Result(true, artifactPath.toString());
        }

        if (safetensorCheckpoint.test(localPath) || safetensorWeightFile.test(localPath)) {
            standardOutput.accept("Provider 'libtorch' selected; using safetensor bridge mode for this checkpoint.");
            return new Result(true, localPath);
        }

        standardError.accept("Error: Provider 'libtorch' currently needs a runnable TorchScript-style artifact "
                + "(.pt, .pts, .pth, .bin).");
        standardError.accept("Resolved local path: " + localPath);
        return new Result(false, localPath);
    }

    private static ProviderArtifactResolver.Artifact artifact(ModelInfo modelInfo) {
        String path = null;
        if (modelInfo != null && modelInfo.getMetadata() != null) {
            Object value = modelInfo.getMetadata().get("path");
            path = value == null ? null : value.toString();
        }
        return new ProviderArtifactResolver.Artifact(
                modelInfo == null ? null : modelInfo.getModelId(),
                modelInfo == null ? null : modelInfo.getShortId(),
                path,
                modelInfo == null ? null : modelInfo.getFormat());
    }
}

package tech.kayys.aqli.cli.commands;

import java.util.function.Predicate;

/**
 * Resolves the provider identity used in execution-route reporting.
 *
 * <p>A LibTorch-selected route backed by a local Safetensors checkpoint or weight file is
 * reported as the Safetensors execution provider. Artifact recognition remains supplied by
 * the CLI adapter so this class does not own filesystem or model-format policy.</p>
 */
final class RouteExecutionProviderResolver {

    private RouteExecutionProviderResolver() {}

    static String resolve(
            String provider,
            String localPath,
            Predicate<String> isSafetensorCheckpointDir,
            Predicate<String> isSafetensorWeightFile) {
        if (!"libtorch".equalsIgnoreCase(provider)
                || localPath == null
                || localPath.isBlank()) {
            return provider;
        }

        if (isSafetensorCheckpointDir.test(localPath) || isSafetensorWeightFile.test(localPath)) {
            return "safetensor";
        }
        return provider;
    }
}

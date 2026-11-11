package tech.kayys.aqli.cli.commands;

import java.util.Locale;

/**
 * Pure eligibility rules for the optional standalone inference routes.
 *
 * <p>This class deliberately owns routing policy only. Argument construction,
 * model resolution, execution, and fallback handling remain with the
 * format-specific standalone runners and the full CLI.</p>
 */
final class StandaloneRunPolicy {
    private StandaloneRunPolicy() {
    }

    static boolean shouldUseLiteRtStandalone(
            boolean routeReportJson,
            boolean enabled,
            String prompt,
            String providerId,
            boolean liteRtModelPath,
            boolean simpleTextRun) {
        return !routeReportJson
                && enabled
                && hasPrompt(prompt)
                && isUnspecifiedOrLiteRtProvider(providerId)
                && liteRtModelPath
                && simpleTextRun;
    }

    static boolean shouldUseCachedGemma4LiteRtStandalone(
            boolean enabled,
            String modelId,
            String modelFile,
            String modelDir,
            String modelPath,
            String requestedProviderId,
            boolean providerExplicit,
            boolean simpleTextRun) {
        return enabled
                && hasPrompt(modelId)
                && isBlank(modelFile)
                && isBlank(modelDir)
                && (modelPath == null || modelPath.isEmpty())
                && isUnspecifiedOrLiteRtProvider(requestedProviderId)
                && (!providerExplicit || isLiteRtProvider(requestedProviderId))
                && simpleTextRun;
    }

    static boolean shouldUseGgufStandalone(
            boolean routeReportJson,
            String prompt,
            String providerId,
            boolean ggufProvider,
            boolean ggufPlugin,
            boolean explicitGgufEngine,
            boolean safetensorModel,
            boolean explicitLlamaCppEngine,
            boolean forceGguf,
            boolean ggufFormat,
            boolean ggufModelFile,
            boolean ggufModelPath,
            boolean ggufModelId) {
        if (routeReportJson || !hasPrompt(prompt)) {
            return false;
        }
        if (!isBlank(providerId) && !ggufProvider) {
            return false;
        }
        return ggufProvider
                || ggufPlugin
                || (explicitGgufEngine && (!safetensorModel || explicitLlamaCppEngine))
                || forceGguf
                || ggufFormat
                || ggufModelFile
                || ggufModelPath
                || ggufModelId;
    }

    private static boolean hasPrompt(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isUnspecifiedOrLiteRtProvider(String value) {
        return isBlank(value) || isLiteRtProvider(value);
    }

    private static boolean isLiteRtProvider(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("litert") || normalized.equals("litertlm");
    }
}

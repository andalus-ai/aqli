package tech.kayys.aqli.cli.commands;

import java.util.Locale;

/**
 * Applies CLI routing policy for model sources and remote execution providers.
 *
 * <p>Repository-reference parsing belongs to {@link ModelSourceResolver}; this
 * class decides whether a Hugging Face reference should be resolved locally
 * for the selected CLI provider.</p>
 */
final class ModelSourceRoutingPolicy {
    private ModelSourceRoutingPolicy() {
    }

    static boolean isRemoteProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        return switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "openai", "mistral", "anthropic", "gemini", "cerebras", "deepseek", "ollama" -> true;
            default -> false;
        };
    }

    static boolean requiresLocalHfRepositoryResolution(
            String requestedModel,
            String requestedProvider,
            boolean mcpProvider) {
        return ModelSourceResolver.huggingFaceRepositoryId(requestedModel) != null
                && !isRemoteProvider(requestedProvider)
                && !mcpProvider;
    }
}

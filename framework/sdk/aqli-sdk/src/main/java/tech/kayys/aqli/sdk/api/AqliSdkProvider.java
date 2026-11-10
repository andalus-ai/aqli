package tech.kayys.aqli.sdk.api;

import java.util.Map;

/**
 * Service Provider Interface for resolving SDK instances.
 * <p>
 * Enables automatic discovery of inference providers via {@link java.util.ServiceLoader}.
 * Each provider registers itself under {@code META-INF/services/tech.kayys.aqli.sdk.api.AqliSdkProvider}.
 *
 * <h3>Provider Priority</h3>
 * When multiple providers are available and no explicit preference is set,
 * the provider with the highest {@link #priority()} value wins.
 * Local inference providers should use priority 100+; cloud providers should use 50.
 *
 * <h3>Example Implementation</h3>
 * <pre>{@code
 * public class GeminiSdkProvider implements AqliSdkProvider {
 *     public String name() { return "gemini"; }
 *     public int priority() { return 50; }
 *     public AqliSdk create(Map<String, Object> config) {
 *         return new GeminiSdk(config);
 *     }
 * }
 * }</pre>
 */
public interface AqliSdkProvider {

    /**
     * Creates a new instance of the Aqli SDK with the given configuration.
     *
     * @param config configuration map containing keys like "model", "endpoint",
     *               "apiKey", "provider", etc. May be {@code null} for default config.
     * @return the SDK instance
     */
    AqliSdk create(Map<String, Object> config);

    /**
     * Returns the unique name of this provider (e.g., "openai", "gemini", "aqli-local").
     * <p>
     * Used by {@link AqliSdkBuilder#provider(String)} to target a specific provider.
     *
     * @return provider name, lowercase
     */
    default String name() {
        return getClass().getSimpleName().toLowerCase().replace("sdkprovider", "");
    }

    /**
     * Returns the priority of this provider. Higher values = higher priority.
     * <p>
     * When the user doesn't specify a preferred provider, the provider with
     * the highest priority is selected.
     *
     * @return priority value (default: 0)
     */
    default int priority() {
        return 0;
    }

    /**
     * Returns whether this provider supports multimodal inputs.
     *
     * @return {@code true} if multimodal processing is supported
     */
    default boolean supportsMultimodal() {
        return false;
    }

    /**
     * Returns whether this provider supports embeddings.
     *
     * @return {@code true} if embedding generation is supported
     */
    default boolean supportsEmbeddings() {
        return false;
    }
}

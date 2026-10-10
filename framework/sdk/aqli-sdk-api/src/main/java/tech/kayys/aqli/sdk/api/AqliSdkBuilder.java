package tech.kayys.aqli.sdk.api;

import java.util.*;

/**
 * Fluent builder for constructing {@link AqliSdk} instances.
 * <p>
 * Discovers available {@link AqliSdkProvider} implementations via
 * {@link ServiceLoader} and resolves the best match based on configuration.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Auto-resolve the first available provider
 * var sdk = AqliSdk.builder()
 *     .model("llama3")
 *     .build();
 *
 * // Target a specific cloud provider
 * var sdk = AqliSdk.builder()
 *     .provider("gemini")
 *     .apiKey(System.getenv("GEMINI_API_KEY"))
 *     .model("gemini-2.0-flash")
 *     .build();
 *
 * // Custom endpoint (e.g., local Aqli server)
 * var sdk = AqliSdk.builder()
 *     .endpoint("http://localhost:8080")
 *     .model("Qwen/Qwen2.5-0.5B")
 *     .build();
 * }</pre>
 */
public final class AqliSdkBuilder {

    private String provider;
    private String model;
    private String endpoint;
    private String apiKey;
    private final Map<String, Object> config = new LinkedHashMap<>();

    AqliSdkBuilder() {}

    /**
     * Sets the preferred provider name (e.g., "openai", "gemini", "anthropic", "aqli-local").
     * If not set, the first available provider on the classpath is used.
     *
     * @param provider provider identifier
     * @return this builder
     */
    public AqliSdkBuilder provider(String provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Sets the default model to use for requests.
     *
     * @param model model identifier (e.g., "gpt-4o", "gemini-2.0-flash", "llama3")
     * @return this builder
     */
    public AqliSdkBuilder model(String model) {
        this.model = model;
        return this;
    }

    /**
     * Sets the API endpoint URL for the inference service.
     *
     * @param endpoint base URL (e.g., "http://localhost:8080")
     * @return this builder
     */
    public AqliSdkBuilder endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * Sets the API key for authentication.
     *
     * @param apiKey the API key
     * @return this builder
     */
    public AqliSdkBuilder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Adds a custom configuration parameter.
     *
     * @param key   configuration key
     * @param value configuration value
     * @return this builder
     */
    public AqliSdkBuilder config(String key, Object value) {
        this.config.put(key, value);
        return this;
    }

    /**
     * Adds all entries from the given configuration map.
     *
     * @param config configuration map
     * @return this builder
     */
    public AqliSdkBuilder config(Map<String, Object> config) {
        this.config.putAll(config);
        return this;
    }

    /**
     * Builds a {@link AqliSdk} instance by resolving a provider via ServiceLoader.
     *
     * @return configured SDK instance
     * @throws IllegalStateException if no suitable provider is found
     */
    public AqliSdk build() {
        // Assemble config map
        Map<String, Object> effectiveConfig = new LinkedHashMap<>(config);
        if (model != null) effectiveConfig.put("model", model);
        if (endpoint != null) effectiveConfig.put("endpoint", endpoint);
        if (apiKey != null) effectiveConfig.put("apiKey", apiKey);
        if (provider != null) effectiveConfig.put("provider", provider);

        // Discover providers via ServiceLoader
        ServiceLoader<AqliSdkProvider> loader = ServiceLoader.load(AqliSdkProvider.class);
        List<AqliSdkProvider> providers = new ArrayList<>();
        loader.forEach(providers::add);

        if (providers.isEmpty()) {
            throw new IllegalStateException(
                    "No AqliSdkProvider found on the classpath. " +
                    "Add a provider dependency (e.g., aqli-sdk-core, aqli-plugin-openai).");
        }

        // If a specific provider is requested, find it
        if (provider != null) {
            for (AqliSdkProvider p : providers) {
                if (provider.equalsIgnoreCase(p.name())) {
                    return p.create(effectiveConfig);
                }
            }
            throw new IllegalStateException(
                    "Requested provider '" + provider + "' not found. Available: " +
                    providers.stream().map(AqliSdkProvider::name).toList());
        }

        // Use highest-priority provider
        providers.sort(Comparator.comparingInt(AqliSdkProvider::priority).reversed());
        return providers.get(0).create(effectiveConfig);
    }
}

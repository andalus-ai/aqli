package tech.kayys.aqli.factory;

import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.sdk.core.AqliSdkProvider;
import tech.kayys.aqli.sdk.exception.SdkException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Factory for creating Aqli SDK instances.
 * Discovers implementations via {@link ServiceLoader} — no hard dependency
 * on local or remote modules.
 *
 * <p>
 * Usage examples:
 *
 * <pre>{@code
 * // Local SDK (embedded in same JVM as engine)
 * // Requires aqli-sdk-java-local on the classpath
 * AqliSdk localSdk = AqliSdkFactory.createLocalSdk();
 *
 * // Local SDK with configuration
 * SdkConfig config = SdkConfig.builder()
 *         .apiKey("community")
 *         .preferredProvider("tech.kayys/ollama-provider")
 *         .build();
 * AqliSdk configuredSdk = AqliSdkFactory.createLocalSdk(config);
 *
 * // Remote SDK (communicates via HTTP)
 * // Requires aqli-sdk-java-remote on the classpath
 * AqliSdk remoteSdk = AqliSdkFactory.builder()
 *         .baseUrl("https://aqli-spi.example.com")
 *         .apiKey("your-api-key")
 *         .buildRemote();
 *
 * // Or use the implementation modules directly without this factory:
 * // - LocalAqliSdk (via CDI injection)
 * // - AqliClient.builder().baseUrl("...").build()
 * }</pre>
 */
public class AqliSdkFactory {

    private AqliSdkFactory() {
    }

    /**
     * Creates a local SDK instance that runs within the same JVM as the inference
     * engine.
     * Discovers the local provider via ServiceLoader.
     *
     * @return A local SDK instance
     * @throws SdkException if SDK creation fails or no local provider is on the
     *                      classpath
     */
    public static AqliSdk createLocalSdk() throws SdkException {
        return createLocalSdk(null);
    }

    // ── Format-aware factory methods (v0.1.4) ─────────────────────────────

    /**
     * Create a local SDK instance pre-configured for GGUF (llama.cpp) inference.
     * Models are looked up in {@code $HOME/.aqli/models/gguf}.
     */
    public static AqliSdk createForGguf() throws SdkException {
        return createLocalSdk(SdkConfig.builder()
                .preferredProvider("gguf")
                .modelFormat("GGUF")
                .build());
    }

    /**
     * Create a local SDK instance for GGUF with an explicit model directory.
     *
     * @param ggufBasePath absolute path to the directory containing {@code .gguf}
     *                     files
     */
    public static AqliSdk createForGguf(String ggufBasePath) throws SdkException {
        return createLocalSdk(SdkConfig.builder()
                .preferredProvider("gguf")
                .modelFormat("GGUF")
                .ggufBasePath(ggufBasePath)
                .build());
    }

    /**
     * Create a local SDK instance pre-configured for SafeTensors (LibTorch)
     * inference.
     * Models are looked up in {@code $HOME/.aqli/models/safetensors}.
     */
    public static AqliSdk createForSafeTensors() throws SdkException {
        return createLocalSdk(SdkConfig.builder()
                .preferredProvider("safetensor")
                .modelFormat("SAFETENSORS")
                .build());
    }

    /**
     * Create a local SDK instance for SafeTensors with an explicit model directory.
     *
     * @param safetensorsBasePath absolute path to the directory containing
     *                            checkpoints
     */
    public static AqliSdk createForSafeTensors(String safetensorsBasePath) throws SdkException {
        return createLocalSdk(SdkConfig.builder()
                .preferredProvider("safetensor")
                .modelFormat("SAFETENSORS")
                .safetensorsBasePath(safetensorsBasePath)
                .build());
    }

    /**
     * Creates a local SDK instance with custom configuration.
     *
     * @param config SDK configuration (may be null for defaults)
     * @return A local SDK instance
     * @throws SdkException if SDK creation fails
     */
    public static AqliSdk createLocalSdk(SdkConfig config) throws SdkException {
        AqliSdkProvider provider = findProvider(AqliSdkProvider.Mode.LOCAL)
                .orElseThrow(() -> new SdkException("SDK_ERR_INIT",
                        "No local SDK provider found. Add aqli-sdk-java-local to your classpath."));
        return provider.create(config);
    }

    /**
     * Creates a remote SDK instance that communicates with the inference engine via
     * HTTP API.
     *
     * @param baseUrl The base URL of the inference engine API
     * @param apiKey  The API key for authentication
     * @return A remote SDK instance
     * @throws SdkException if SDK creation fails
     */
    public static AqliSdk createRemoteSdk(String baseUrl, String apiKey) throws SdkException {
        return builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .buildRemote();
    }

    /**
     * Creates a remote SDK instance with custom configuration.
     *
     * @param config SDK configuration
     * @return A remote SDK instance
     * @throws SdkException if SDK creation fails
     */
    public static AqliSdk createRemoteSdk(SdkConfig config) throws SdkException {
        Objects.requireNonNull(config, "config cannot be null");
        AqliSdkProvider provider = findProvider(AqliSdkProvider.Mode.REMOTE)
                .orElseThrow(() -> new SdkException("SDK_ERR_INIT",
                        "No remote SDK provider found. Add aqli-sdk-java-remote to your classpath."));
        return provider.create(config);
    }

    /**
     * Auto-detect the best available SDK from the classpath.
     * Prefers LOCAL over REMOTE when both are available.
     *
     * @return An SDK instance
     * @throws SdkException if no provider is found
     */
    public static AqliSdk create() throws SdkException {
        return create(null);
    }

    /**
     * Auto-detect the best available SDK from the classpath with configuration.
     *
     * @param config SDK configuration (may be null for defaults)
     * @return An SDK instance
     * @throws SdkException if no provider is found
     */
    public static AqliSdk create(SdkConfig config) throws SdkException {
        AqliSdkProvider provider = ServiceLoader.load(AqliSdkProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .min(Comparator.comparingInt(AqliSdkProvider::priority))
                .orElseThrow(() -> new SdkException("SDK_ERR_INIT",
                        "No SDK provider found. Add aqli-sdk-java-local or aqli-sdk-java-remote to your classpath."));
        return provider.create(config);
    }

    /**
     * Creates a new builder for configuring SDK instances.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Finds a provider for the given mode via ServiceLoader.
     */
    private static Optional<AqliSdkProvider> findProvider(AqliSdkProvider.Mode mode) {
        ServiceLoader<AqliSdkProvider> loader = ServiceLoader.load(AqliSdkProvider.class);
        AqliSdkProvider best = null;
        int bestPriority = Integer.MAX_VALUE;
        var it = loader.iterator();
        while (true) {
            try {
                if (!it.hasNext()) break;
            } catch (Throwable t) {
                // If hasNext fails, stop discovery to avoid ServiceLoader runtime crash
                return Optional.ofNullable(best);
            }
            AqliSdkProvider p = null;
            try {
                p = it.next();
            } catch (Throwable t) {
                // Skip providers that cannot be instantiated (broken service implementations)
                // and continue discovery
                continue;
            }
            try {
                if (p.mode() == mode) {
                    int pr = p.priority();
                    if (pr < bestPriority) {
                        bestPriority = pr;
                        best = p;
                    }
                }
            } catch (Throwable t) {
                // Protect against provider methods throwing; skip on error
                continue;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Builder for creating configured SDK instances.
     */
    public static class Builder {
        private String baseUrl;
        private String apiKey = "community";
        private String preferredProvider;
        private Duration requestTimeout = Duration.ofSeconds(60);
        private Duration connectTimeout = Duration.ofSeconds(30);
        private int maxRetries = 3;
        private boolean enableMetrics = false;
        private String modelFormat; // v0.1.4
        private String ggufBasePath; // v0.1.4
        private String safetensorsBasePath; // v0.1.4

        private Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey cannot be null");
            return this;
        }

        public Builder preferredProvider(String preferredProvider) {
            this.preferredProvider = preferredProvider;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout cannot be null");
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout cannot be null");
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder enableMetrics(boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
            return this;
        }

        public Builder modelFormat(String modelFormat) {
            this.modelFormat = modelFormat;
            return this;
        }

        private SdkConfig toConfig() {
            return SdkConfig.builder()
                    .apiKey(apiKey)
                    .requestTimeout(requestTimeout)
                    .connectTimeout(connectTimeout)
                    .preferredProvider(preferredProvider)
                    .enableMetrics(enableMetrics)
                    .maxRetries(maxRetries)
                    .modelFormat(modelFormat)
                    .ggufBasePath(ggufBasePath)
                    .safetensorsBasePath(safetensorsBasePath)
                    .build();
        }

        /**
         * Builds a local SDK instance with the configured settings.
         *
         * @return A configured local SDK instance
         * @throws SdkException if the local provider is not available
         */
        public AqliSdk buildLocal() throws SdkException {
            return createLocalSdk(toConfig());
        }

        /**
         * Builds a remote SDK instance with the configured settings.
         *
         * @return A configured remote SDK instance
         * @throws SdkException if configuration is invalid or remote provider not
         *                      available
         */
        public AqliSdk buildRemote() throws SdkException {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new SdkException("SDK_ERR_CONFIG", "baseUrl is required for remote SDK");
            }
            return createRemoteSdk(toConfig());
        }

        /**
         * Auto-detect the best available SDK from the classpath.
         *
         * @return A configured SDK instance
         * @throws SdkException if no provider is found
         */
        public AqliSdk build() throws SdkException {
            return create(toConfig());
        }
    }
}

package tech.kayys.aqli.sdk.internal;

import tech.kayys.aqli.sdk.AqliClient;
import tech.kayys.aqli.spi.inference.InferenceEngine;

import java.time.Duration;

/**
 * Default implementation of {@link AqliClient.Builder} that auto-detects whether
 * to create a local (in-process) or remote (HTTP) client.
 *
 * <p>Selection logic:
 * <ol>
 *   <li>If {@link #local()} was called, always use the local engine.</li>
 *   <li>If no endpoint is set but {@code InferenceService} is on the classpath, use local.</li>
 *   <li>If an endpoint is set, use the remote HTTP client.</li>
 * </ol>
 */
public class AqliClientBuilderImpl implements AqliClient.Builder {

    /** Remote server endpoint URL, or {@code null} for local mode. */
    String endpoint;
    /** Default model identifier passed to the client. */
    String model;
    /** API key used for authentication. Defaults to {@code "empty"}. */
    String apiKey = "empty";
    /** Request timeout in milliseconds. Defaults to 30 seconds. */
    long timeoutMillis = 30000;
    /** When {@code true}, forces local in-process mode regardless of classpath detection. */
    boolean forceLocal = false;

    @Override
    public AqliClient.Builder endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    @Override
    public AqliClient.Builder local() {
        this.forceLocal = true;
        return this;
    }

    @Override
    public AqliClient.Builder model(String model) {
        this.model = model;
        return this;
    }

    @Override
    public AqliClient.Builder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    @Override
    public AqliClient.Builder timeoutMillis(long timeout) {
        this.timeoutMillis = timeout;
        return this;
    }

    /**
     * Builds the appropriate {@link AqliClient} based on the configured options.
     *
     * @return a local or remote {@link AqliClient} instance
     * @throws IllegalStateException if neither an endpoint nor a local engine is available
     */
    @Override
    public AqliClient build() {
        // Discovery logic
        if (forceLocal || (endpoint == null && isEnginePresent())) {
            return buildLocal();
        }

        if (endpoint != null) {
            return new RemoteAqliClient(this);
        }

        throw new IllegalStateException("No Aqli configuration provided. " +
                "Either provide an .endpoint('...') for remote mode, " +
                "or ensure the aqli-engine is on the classpath for .local() mode.");
    }

    /**
     * Checks whether the Aqli inference engine is available on the classpath.
     *
     * @return {@code true} if {@code InferenceService} can be loaded
     */
    private boolean isEnginePresent() {
        try {
            Class.forName("tech.kayys.aqli.engine.inference.InferenceService");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Resolves the {@link InferenceService} from the CDI container and wraps it
     * in a {@link LocalAqliClient}.
     *
     * @return a {@link LocalAqliClient} backed by the in-process engine
     * @throws RuntimeException if the CDI container is unavailable or the service is not initialized
     */
    private AqliClient buildLocal() {
        try {
            // In a Quarkus app, we'd use Arc.container().instance(InferenceService.class).get()
            Class<?> arcClass = Class.forName("io.quarkus.arc.Arc");
            Object container = arcClass.getMethod("container").invoke(null);
            Object instance = container.getClass().getMethod("instance", Class.class, java.lang.annotation.Annotation[].class)
                    .invoke(container, InferenceEngine.class, new java.lang.annotation.Annotation[0]);
            
            InferenceEngine service = (InferenceEngine) instance.getClass().getMethod("get").invoke(instance);
            
            if (service == null) {
                throw new IllegalStateException("InferenceService found on classpath but not initialized in CDI container. " +
                        "Local mode requires a running Quarkus/Aqli environment.");
            }
            
            return new LocalAqliClient(service, model);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize LocalAqliClient. " +
                    "Ensure the Aqli engine is properly started in this process.", e);
        }
    }
}

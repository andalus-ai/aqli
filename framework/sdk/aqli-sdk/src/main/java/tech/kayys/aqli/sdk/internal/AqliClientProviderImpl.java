package tech.kayys.aqli.sdk.internal;

import tech.kayys.aqli.sdk.api.AqliClient;
import tech.kayys.aqli.sdk.api.AqliClientProvider;
import tech.kayys.aqli.sdk.api.ClientTransport;
import tech.kayys.aqli.sdk.api.DeploymentMode;

/** Service provider for the optional SDK implementation artifact. */
public final class AqliClientProviderImpl implements AqliClientProvider {
    @Override
    public AqliClient.Builder createBuilder() {
        return new TransportAqliClientBuilder();
    }
}

final class TransportAqliClientBuilder implements AqliClient.Builder {
    private String model;
    private String endpoint;
    private String backend;
    private int maxTokens = 512;
    private float temperature = 0.7f;
    private ClientTransport transport;
    private DeploymentMode legacyMode;

    @Override
    public AqliClient.Builder model(String model) {
        this.model = java.util.Objects.requireNonNull(model, "model");
        return this;
    }

    @Override
    public AqliClient.Builder endpoint(String endpoint) {
        this.endpoint = java.util.Objects.requireNonNull(endpoint, "endpoint");
        return this;
    }

    @Override
    public AqliClient.Builder maxTokens(int maxTokens) {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        this.maxTokens = maxTokens;
        return this;
    }

    @Override
    public AqliClient.Builder temperature(float temperature) {
        if (!Float.isFinite(temperature) || temperature < 0.0f || temperature > 2.0f) {
            throw new IllegalArgumentException("temperature must be finite and in [0, 2]");
        }
        this.temperature = temperature;
        return this;
    }

    @Override
    public AqliClient.Builder backend(String backend) {
        this.backend = backend;
        return this;
    }

    @Override
    public AqliClient.Builder transport(ClientTransport transport) {
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        return this;
    }

    @Override
    @Deprecated(since = "0.2", forRemoval = false)
    public AqliClient.Builder mode(DeploymentMode mode) {
        this.legacyMode = java.util.Objects.requireNonNull(mode, "mode");
        return this;
    }

    @Override
    public AqliClient build() {
        if (transport != null && legacyMode != null) {
            throw new IllegalStateException("Configure either transport() or deprecated mode(), not both");
        }
        DeploymentMode selectedMode = legacyMode;
        if (transport != null) {
            selectedMode = switch (transport) {
                case IN_PROCESS -> DeploymentMode.EMBEDDED;
                case GRPC -> DeploymentMode.GRPC;
                case REST -> DeploymentMode.REST;
            };
        }
        return AqliClientFactory.build(
                selectedMode, endpoint, model, backend, maxTokens, temperature);
    }
}

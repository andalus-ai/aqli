package tech.kayys.aqli.sdk.internal;

import tech.kayys.aqli.sdk.api.DeploymentMode;
import tech.kayys.aqli.sdk.api.AqliClient;
import tech.kayys.aqli.spi.inference.InferenceEngine;

public class AqliClientFactory {

    public static AqliClient build(DeploymentMode mode, String endpoint, String model, String backend, int maxTokens, float temperature) {
        if (mode == null) {
            mode = (endpoint != null) ? DeploymentMode.REST : DeploymentMode.EMBEDDED;
        }

        switch (mode) {
            case EMBEDDED:
                return buildEmbedded(model, backend, maxTokens, temperature);
            case REST:
                return buildRest(endpoint, model, backend, maxTokens, temperature);
            case GRPC:
                return buildGrpc(endpoint, model, backend, maxTokens, temperature);
            case CLI:
                return buildCli(model, backend, maxTokens, temperature);
            default:
                throw new IllegalArgumentException("Unsupported DeploymentMode: " + mode);
        }
    }

    private static AqliClient buildEmbedded(String model, String backend, int maxTokens, float temperature) {
        try {
            Class<?> arcClass = Class.forName("io.quarkus.arc.Arc");
            Object container = arcClass.getMethod("container").invoke(null);
            Object instance = container.getClass().getMethod("instance", Class.class, java.lang.annotation.Annotation[].class)
                    .invoke(container, InferenceEngine.class, new java.lang.annotation.Annotation[0]);
            InferenceEngine service = (InferenceEngine) instance.getClass().getMethod("get").invoke(instance);
            if (service == null) {
                throw new IllegalStateException("InferenceEngine not initialized in CDI container.");
            }
            return new EmbeddedAqliClient(service, model, backend, maxTokens, temperature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize EmbeddedAqliClient", e);
        }
    }

    private static AqliClient buildRest(String endpoint, String model, String backend, int maxTokens, float temperature) {
        if (endpoint == null) {
            throw new IllegalArgumentException("Endpoint is required for REST mode");
        }
        return new RestAqliClient(endpoint, model, backend, maxTokens, temperature);
    }

    private static AqliClient buildGrpc(String endpoint, String model, String backend, int maxTokens, float temperature) {
        if (endpoint == null) {
            throw new IllegalArgumentException("Endpoint is required for GRPC mode");
        }
        return new GrpcAqliClient(endpoint, model, backend, maxTokens, temperature);
    }

    private static AqliClient buildCli(String model, String backend, int maxTokens, float temperature) {
        return new CliAqliClient(model, backend, maxTokens, temperature);
    }
}

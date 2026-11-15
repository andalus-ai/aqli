package tech.kayys.aqli.cli.commands.request;

import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.util.Map;

/**
 * Applies runtime routing hints resolved by the CLI to an inference request.
 *
 * <p>This is a CLI adapter: route discovery and policy remain in the runtime,
 * while the SDK/application contract defines the transport-neutral request
 * semantics shared by CLI, REST, and gRPC.</p>
 */
public final class RuntimeRoutingRunRequestConfiguration {

    private RuntimeRoutingRunRequestConfiguration() {
    }

    public record Options(
            Map<String, Object> routeMetadata,
            boolean runnerExplicitlyRequested,
            String normalizedRunner,
            String ggufEngineHint,
            String featurePipelineId,
            String preferredProvider,
            boolean libtorchSafetensorBridge) {
    }

    public static void apply(InferenceRequest.Builder request, Options options) {
        if (request == null || options == null) {
            throw new IllegalArgumentException("request and runtime routing options are required");
        }

        if (options.routeMetadata() != null && !options.routeMetadata().isEmpty()) {
            request.metadata(options.routeMetadata());
        }
        if (options.runnerExplicitlyRequested()
                && options.normalizedRunner() != null
                && !options.normalizedRunner().isBlank()) {
            request.parameter("runner", options.normalizedRunner());
            request.metadata("runner", options.normalizedRunner());
        }
        addHint(request, "gguf.backend", options.ggufEngineHint(), true);
        if (options.featurePipelineId() != null && !options.featurePipelineId().isBlank()) {
            String pipelineId = options.featurePipelineId().trim();
            request.parameter("pipeline", pipelineId);
            request.parameter("feature", pipelineId);
            request.parameter("feature_pipeline", true);
        }
        if (options.preferredProvider() != null && !options.preferredProvider().isEmpty()) {
            request.preferredProvider(options.preferredProvider());
        }
        if (options.libtorchSafetensorBridge()) {
            request.metadata("requested_provider", "libtorch");
            request.metadata("effective_provider", "safetensor");
            request.metadata("provider_bridge_mode", "cli_libtorch_to_safetensor");
            request.metadata("provider_bridge_reason", "raw_safetensor_checkpoint");
        }
    }

    private static void addHint(
            InferenceRequest.Builder request,
            String key,
            String value,
            boolean includeParameter) {
        if (value == null || value.isBlank()) {
            return;
        }
        request.metadata(key, value);
        if (includeParameter) {
            request.parameter(key, value);
        }
    }
}

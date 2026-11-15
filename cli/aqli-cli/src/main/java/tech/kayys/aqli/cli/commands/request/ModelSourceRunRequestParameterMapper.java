package tech.kayys.aqli.cli.commands.request;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Maps the CLI's resolved model source onto the internal inference request.
 *
 * <p>Resolution itself remains in the CLI/runtime. This adapter only carries
 * the resolved local path and original model reference into execution.</p>
 */
public final class ModelSourceRunRequestParameterMapper {

    private ModelSourceRunRequestParameterMapper() {
    }

    public record Options(
            boolean customModelPathUsed,
            String localModelPath,
            String requestedModelRef) {
    }

    public static void apply(InferenceRequest.Builder request, Options options) {
        if (request == null || options == null) {
            throw new IllegalArgumentException("request and model-source options are required");
        }

        if (options.customModelPathUsed() && options.localModelPath() != null) {
            request.parameter("model_path", options.localModelPath());
        }
        if (options.requestedModelRef() != null && !options.requestedModelRef().isBlank()) {
            request.parameter("requested_model_ref", options.requestedModelRef());
        }
    }
}

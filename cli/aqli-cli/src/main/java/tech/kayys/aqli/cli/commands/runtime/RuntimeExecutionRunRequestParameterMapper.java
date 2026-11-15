package tech.kayys.aqli.cli.commands.runtime;

import java.util.Map;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Maps CLI runtime and execution-routing options onto the internal inference request.
 *
 * <p>This keeps runner selection, GGUF engine hints, profiling and quantization
 * separate from modality-specific request parameters.</p>
 */
public final class RuntimeExecutionRunRequestParameterMapper {

    private RuntimeExecutionRunRequestParameterMapper() {
    }

    public static void apply(InferenceRequest.Builder request, Options options) {
        if (request == null || options == null) {
            throw new IllegalArgumentException("request and runtime execution options are required");
        }

        if (options.quantizeStrategy() != null && !options.quantizeStrategy().isBlank()) {
            request.parameter("quantize_strategy", options.quantizeStrategy());
            request.parameter("quantize_bits", options.quantizeBits());
        }
        if (options.quantizeKv() != null && !options.quantizeKv().equalsIgnoreCase("none")) {
            request.parameter("kv_cache_quant", options.quantizeKv());
        }
        if (options.runtimeProfile()) {
            request.parameter("profile", true);
            request.parameter("onnx_profile", true);
            request.metadata("profile", true);
            request.metadata("onnx_profile", true);
        }

        if (options.runnerRouteMetadata() != null) {
            request.metadata(options.runnerRouteMetadata());
        }
        if (options.runner() != null && !options.runner().isBlank()) {
            request.parameter("runner", options.normalizedRunner());
            request.metadata("runner", options.normalizedRunner());
        }
        if (options.ggufEngineHint() != null && !options.ggufEngineHint().isBlank()) {
            request.metadata("gguf.backend", options.ggufEngineHint());
            request.parameter("gguf.backend", options.ggufEngineHint());
        }
    }

    public record Options(
            String quantizeStrategy,
            Integer quantizeBits,
            String quantizeKv,
            boolean runtimeProfile,
            Map<String, Object> runnerRouteMetadata,
            String runner,
            String normalizedRunner,
            String ggufEngineHint) {
    }
}

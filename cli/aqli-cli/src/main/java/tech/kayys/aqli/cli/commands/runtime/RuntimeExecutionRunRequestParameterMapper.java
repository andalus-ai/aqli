package tech.kayys.aqli.cli.commands.runtime;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Maps CLI quantization and profiling options onto the internal inference request.
 *
 * <p>Runner selection and provider routing are applied separately by
 * {@code RuntimeRoutingRunRequestConfiguration}; modality-specific options
 * belong to their capability mappers.</p>
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
    }

    public record Options(
            String quantizeStrategy,
            Integer quantizeBits,
            String quantizeKv,
            boolean runtimeProfile) {
    }
}

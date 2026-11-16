package tech.kayys.aqli.cli.commands.request;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Applies quantization options resolved by the CLI to the shared inference request.
 *
 * <p>This class is a transport adapter. Runtime policy and backend support remain
 * outside the CLI, while the request fields stay consistent across transports.</p>
 */
public final class RuntimeQuantizationRequestConfiguration {

    private RuntimeQuantizationRequestConfiguration() {
    }

    public static void apply(
            InferenceRequest.Builder request,
            String quantizeStrategy,
            int quantizeBits,
            String quantizeKv) {
        if (quantizeStrategy != null && !quantizeStrategy.isBlank()) {
            request.parameter("quantize_strategy", quantizeStrategy);
            request.parameter("quantize_bits", quantizeBits);
        }

        if (quantizeKv != null && !quantizeKv.equalsIgnoreCase("none")) {
            request.parameter("kv_cache_quant", quantizeKv);
        }
    }
}

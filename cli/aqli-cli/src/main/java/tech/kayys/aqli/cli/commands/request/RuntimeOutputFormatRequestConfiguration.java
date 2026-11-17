package tech.kayys.aqli.cli.commands.request;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Applies the CLI-selected output format to the shared inference request.
 */
public final class RuntimeOutputFormatRequestConfiguration {

    private RuntimeOutputFormatRequestConfiguration() {
    }

    public static void apply(InferenceRequest.Builder request, String extension) {
        if (extension != null && !extension.isBlank()) {
            request.parameter("output_format", extension.toLowerCase());
        }
    }
}

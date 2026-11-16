package tech.kayys.aqli.cli.commands.request;

import tech.kayys.aqli.api.inference.InferenceRequest;

/**
 * Applies profiling flags to the shared inference request.
 */
public final class RuntimeProfilingRequestConfiguration {

    private RuntimeProfilingRequestConfiguration() {
    }

    public static void apply(InferenceRequest.Builder request, boolean enabled) {
        if (!enabled) {
            return;
        }

        request.parameter("profile", true);
        request.parameter("onnx_profile", true);
        request.metadata("profile", true);
        request.metadata("onnx_profile", true);
    }
}

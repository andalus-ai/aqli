package tech.kayys.aqli.sdk.core;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Applies transport-neutral text-generation options to the internal execution
 * request. This is a compatibility bridge while the public capability request
 * envelope is being established.
 */
public final class TextGenerationRequestMapper {

    private TextGenerationRequestMapper() {
    }

    public static void apply(InferenceRequest.Builder request, TextGenerationOptions options) {
        if (request == null || options == null) {
            throw new IllegalArgumentException("request and text-generation options are required");
        }

        request.temperature(options.temperature());
        request.topP(options.topP());
        request.topK(options.topK());
        request.repeatPenalty(options.repeatPenalty());
        request.jsonMode(options.jsonMode());
        request.maxTokens(options.maxTokens());

        if (options.mirostat() > 0) {
            request.mirostat(options.mirostat());
        }
        if (options.grammar() != null && !options.grammar().isEmpty()) {
            request.grammar(options.grammar());
        }
    }
}

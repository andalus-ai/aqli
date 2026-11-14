package tech.kayys.aqli.cli.commands.text;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Maps text-generation CLI options onto the inference request contract.
 *
 * <p>This is a CLI adapter, not the canonical generation API. The
 * transport-neutral SDK capability contract should own these semantics so
 * CLI, REST, and gRPC can share the same request model.</p>
 */
public final class TextRunRequestParameterMapper {

    private TextRunRequestParameterMapper() {
    }

    public record Options(
            double temperature,
            double topP,
            int topK,
            double repeatPenalty,
            boolean jsonMode,
            int maxTokens,
            int mirostat,
            String grammar) {
    }

    public static void apply(InferenceRequest.Builder request, Options options) {
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

package tech.kayys.aqli.cli.commands.text;

import tech.kayys.aqli.sdk.core.TextGenerationOptions;
import tech.kayys.aqli.sdk.core.TextGenerationRequestMapper;
import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Adapts Picocli's text-generation options to the shared SDK request mapper.
 *
 * <p>CLI owns argument parsing and its option grouping; the SDK owns the
 * transport-neutral mapping semantics.</p>
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
        if (options == null) {
            throw new IllegalArgumentException("text-generation options are required");
        }
        TextGenerationRequestMapper.apply(request, new TextGenerationOptions(
                options.temperature(),
                options.topP(),
                options.topK(),
                options.repeatPenalty(),
                options.jsonMode(),
                options.maxTokens(),
                options.mirostat(),
                options.grammar()));
    }
}

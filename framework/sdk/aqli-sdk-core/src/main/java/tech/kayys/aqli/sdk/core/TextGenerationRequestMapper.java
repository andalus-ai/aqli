package tech.kayys.aqli.sdk.core;

import tech.kayys.aqli.sdk.capability.text.TextGenerationMessage;
import tech.kayys.aqli.sdk.capability.text.TextGenerationParameters;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.util.Objects;

/**
 * Maps the public typed text capability request into the internal execution SPI.
 *
 * <p>This is the single compatibility boundary for text-generation request
 * normalization. Transport adapters must not map directly to InferenceRequest.</p>
 */
public final class TextGenerationRequestMapper {

    private TextGenerationRequestMapper() {
    }

    public static InferenceRequest toInferenceRequest(TextGenerationRequest request) {
        return toInferenceRequest(request, false);
    }

    public static InferenceRequest toStreamingInferenceRequest(TextGenerationRequest request) {
        return toInferenceRequest(request, true);
    }

    private static InferenceRequest toInferenceRequest(TextGenerationRequest request, boolean streaming) {
        Objects.requireNonNull(request, "request");
        TextGenerationParameters parameters = request.parameters();

        InferenceRequest.Builder builder = InferenceRequest.builder()
                .model(request.model())
                .messages(request.messages().stream().map(TextGenerationRequestMapper::toSpiMessage).toList())
                .streaming(streaming);

        if (parameters.temperature() != null) {
            builder.temperature(parameters.temperature());
        }
        if (parameters.topP() != null) {
            builder.topP(parameters.topP());
        }
        if (parameters.topK() != null) {
            builder.topK(parameters.topK());
        }
        if (parameters.maxTokens() != null) {
            builder.maxTokens(parameters.maxTokens());
        }
        if (parameters.repeatPenalty() != null) {
            builder.repeatPenalty(parameters.repeatPenalty());
        }
        if (parameters.minP() != null) {
            builder.minP(parameters.minP());
        }
        if (parameters.seed() != null) {
            builder.seed(parameters.seed());
        }
        if (parameters.jsonMode() != null) {
            builder.jsonMode(parameters.jsonMode());
        }
        if (parameters.grammar() != null && !parameters.grammar().isBlank()) {
            builder.grammar(parameters.grammar());
        }
        if (!parameters.stopSequences().isEmpty()) {
            builder.parameter("stop", parameters.stopSequences());
        }
        return builder.build();
    }

    private static Message toSpiMessage(TextGenerationMessage message) {
        Message.Role role = Message.Role.valueOf(message.role().name());
        return new Message(role, message.content(), null, message.name(), null, message.toolCallId());
    }

    /**
     * Compatibility overload retained for callers still using the former
     * SDK-core options value while transports migrate to the public contract.
     */
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

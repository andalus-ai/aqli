package tech.kayys.aqli.sdk.core;

import tech.kayys.aqli.sdk.capability.text.TextGenerationMessage;
import tech.kayys.aqli.sdk.capability.text.TextGenerationContext;
import tech.kayys.aqli.sdk.capability.text.TextGenerationContextItem;
import tech.kayys.aqli.sdk.capability.text.TextGenerationParameters;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.sdk.capability.text.TextGenerationToolDefinition;
import tech.kayys.aqli.sdk.capability.text.TextGenerationToolCall;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.util.Objects;

/** Maps the public typed text capability request into the internal execution SPI. */
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
                .messages(toSpiMessages(request))
                .streaming(streaming)
                .toolChoice(request.toolChoice())
                .metadata(request.metadata());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            builder.apiKey(request.apiKey());
        }
        if (request.requestId() != null) builder.requestId(request.requestId());
        if (request.userId() != null) builder.userId(request.userId());
        if (request.sessionId() != null) builder.sessionId(request.sessionId());
        if (request.traceId() != null) builder.traceId(request.traceId());

        applyContext(builder, request.context());

        if (!request.tools().isEmpty()) {
            builder.tools(request.tools().stream()
                    .map(TextGenerationRequestMapper::toSpiToolDefinition)
                    .toList());
        }
        if (parameters.temperature() != null) builder.temperature(parameters.temperature());
        if (parameters.topP() != null) builder.topP(parameters.topP());
        if (parameters.topK() != null) builder.topK(parameters.topK());
        if (parameters.maxTokens() != null) builder.maxTokens(parameters.maxTokens());
        if (parameters.repeatPenalty() != null) builder.repeatPenalty(parameters.repeatPenalty());
        if (parameters.minP() != null) builder.minP(parameters.minP());
        if (parameters.seed() != null) builder.seed(parameters.seed());
        if (parameters.jsonMode() != null) builder.jsonMode(parameters.jsonMode());
        if (parameters.grammar() != null && !parameters.grammar().isBlank()) {
            builder.grammar(parameters.grammar());
        }
        if (!parameters.stopSequences().isEmpty()) {
            builder.parameter("stop", parameters.stopSequences());
        }
        return builder.build();
    }

    private static java.util.List<Message> toSpiMessages(TextGenerationRequest request) {
        java.util.List<Message> messages = new java.util.ArrayList<>();
        TextGenerationContext context = request.context();
        if (context != null && !context.items().isEmpty() && !Boolean.FALSE.equals(context.enabled())) {
            StringBuilder prompt = new StringBuilder("Retrieved context supplied by the caller. Use it when relevant.");
            for (int i = 0; i < context.items().size(); i++) {
                TextGenerationContextItem item = context.items().get(i);
                prompt.append("\n\n[").append(i + 1).append("]");
                java.util.List<String> labels = new java.util.ArrayList<>();
                if (item.title() != null && !item.title().isBlank()) labels.add(item.title());
                if (item.source() != null && !item.source().isBlank()) labels.add("source: " + item.source());
                if (item.score() != null) labels.add("score: " + item.score());
                if (!labels.isEmpty()) prompt.append(" ").append(String.join(", ", labels));
                prompt.append("\n").append(item.text());
            }
            messages.add(Message.system(prompt.toString()));
        }
        request.messages().stream().map(TextGenerationRequestMapper::toSpiMessage).forEach(messages::add);
        return messages;
    }

    private static void applyContext(InferenceRequest.Builder builder, TextGenerationContext context) {
        if (context == null) return;
        if (context.enabled() != null) {
            builder.parameter("rag_enabled", context.enabled());
            builder.metadata("rag_enabled", context.enabled());
        }
        if (context.embeddingModel() != null && !context.embeddingModel().isBlank()) {
            builder.parameter("embedding_model", context.embeddingModel());
        }
        if (!context.sources().isEmpty()) builder.parameter("rag_sources", context.sources());
        if (context.items().isEmpty() || Boolean.FALSE.equals(context.enabled())) return;
        builder.parameter("rag_context", context.items().stream().map(item -> {
            java.util.Map<String, Object> value = new java.util.LinkedHashMap<>();
            value.put("text", item.text());
            if (item.id() != null) value.put("id", item.id());
            if (item.source() != null) value.put("source", item.source());
            if (item.title() != null) value.put("title", item.title());
            if (item.score() != null) value.put("score", item.score());
            if (!item.metadata().isEmpty()) value.put("metadata", item.metadata());
            return value;
        }).toList());
        builder.metadata("rag_context_injected", true);
        builder.metadata("rag_context_items", context.items().size());
    }

    private static Message toSpiMessage(TextGenerationMessage message) {
        Message.Role role = Message.Role.valueOf(message.role().name());
        return new Message(role, message.content(), null, message.name(), message.toolCalls().stream()
                .map(TextGenerationRequestMapper::toSpiToolCall).toList(), message.toolCallId());
    }

    private static tech.kayys.aqli.spi.tool.ToolCall toSpiToolCall(TextGenerationToolCall call) {
        return tech.kayys.aqli.spi.tool.ToolCall.builder()
                .id(call.id())
                .name(call.name())
                .type(call.type().name().toLowerCase(java.util.Locale.ROOT))
                .arguments(call.arguments())
                .build();
    }

    private static ToolDefinition toSpiToolDefinition(TextGenerationToolDefinition tool) {
        return new ToolDefinition(
                tool.name(),
                ToolDefinition.Type.valueOf(tool.type().name()),
                tool.description(),
                tool.parameters(),
                tool.strict(),
                tool.metadata());
    }

    /** Compatibility overload for the former SDK-core options value. */
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
        if (options.mirostat() > 0) request.mirostat(options.mirostat());
        if (options.grammar() != null && !options.grammar().isEmpty()) request.grammar(options.grammar());
    }
}

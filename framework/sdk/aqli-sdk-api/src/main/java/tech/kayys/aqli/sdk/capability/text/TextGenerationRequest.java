package tech.kayys.aqli.sdk.capability.text;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Transport-neutral text generation request. */
public record TextGenerationRequest(
        String model,
        List<TextGenerationMessage> messages,
        TextGenerationParameters parameters,
        List<TextGenerationToolDefinition> tools,
        Object toolChoice,
        Map<String, Object> metadata,
        String requestId,
        String userId,
        String sessionId,
        String traceId) {
    public TextGenerationRequest {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model must not be blank");
        model = model.trim();
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (messages.isEmpty()) throw new IllegalArgumentException("messages must not be empty");
        if (messages.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("messages must not contain null entries");
        parameters = parameters == null ? TextGenerationParameters.unspecified() : parameters;
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (tools.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("tools must not contain null entries");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Source-compatible constructor for requests with tools but no agentic context. */
    public TextGenerationRequest(String model, List<TextGenerationMessage> messages,
            TextGenerationParameters parameters, List<TextGenerationToolDefinition> tools) {
        this(model, messages, parameters, tools, null, Map.of(), null, null, null, null);
    }
    public TextGenerationRequest(String model, List<TextGenerationMessage> messages, TextGenerationParameters parameters) {
        this(model, messages, parameters, List.of());
    }
    public static TextGenerationRequest of(String model, String prompt) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt must not be blank");
        return new TextGenerationRequest(model, List.of(TextGenerationMessage.user(prompt)), TextGenerationParameters.unspecified());
    }
}

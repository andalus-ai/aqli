package tech.kayys.aqli.sdk.capability.text;

import java.util.Collections;
import java.util.HashMap;
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
        String traceId,
        String apiKey,
        TextGenerationContext context) {

    public TextGenerationRequest {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        model = model.trim();
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        if (messages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("messages must not contain null entries");
        }
        parameters = parameters == null ? TextGenerationParameters.unspecified() : parameters;
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (tools.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("tools must not contain null entries");
        }
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    /** Source-compatible constructor for requests with tools but no agentic context. */
    public TextGenerationRequest(
            String model,
            List<TextGenerationMessage> messages,
            TextGenerationParameters parameters,
            List<TextGenerationToolDefinition> tools) {
        this(model, messages, parameters, tools, null, Map.of(), null, null, null, null, null, null);
    }

    /** Source-compatible constructor for the original agentic-context request shape. */
    public TextGenerationRequest(
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
        this(model, messages, parameters, tools, toolChoice, metadata,
                requestId, userId, sessionId, traceId, null, null);
    }

    /** Source-compatible constructor including the API key field. */
    public TextGenerationRequest(
            String model, List<TextGenerationMessage> messages, TextGenerationParameters parameters,
            List<TextGenerationToolDefinition> tools, Object toolChoice, Map<String, Object> metadata,
            String requestId, String userId, String sessionId, String traceId, String apiKey) {
        this(model, messages, parameters, tools, toolChoice, metadata,
                requestId, userId, sessionId, traceId, apiKey, null);
    }

    public TextGenerationRequest(String model, List<TextGenerationMessage> messages, TextGenerationParameters parameters) {
        this(model, messages, parameters, List.of());
    }

    /**
     * Returns the typed tool-selection policy when this request uses the SDK contract.
     *
     * <p>The legacy {@link #toolChoice()} accessor remains available for
     * source and wire compatibility while callers migrate.</p>
     */
    public java.util.Optional<TextGenerationToolChoice> typedToolChoice() {
        return toolChoice instanceof TextGenerationToolChoice choice
                ? java.util.Optional.of(choice)
                : java.util.Optional.empty();
    }

    /**
     * Returns a copy of this request using the SDK-owned typed tool-selection policy.
     */
    public TextGenerationRequest withToolChoice(TextGenerationToolChoice choice) {
        return new TextGenerationRequest(
                model, messages, parameters, tools, choice, metadata,
                requestId, userId, sessionId, traceId, apiKey, context);
    }

    public static TextGenerationRequest of(String model, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        return new TextGenerationRequest(
                model,
                List.of(TextGenerationMessage.user(prompt)),
                TextGenerationParameters.unspecified());
    }
}

package tech.kayys.aqli.sdk.capability.text;

import java.util.List;
import java.util.Objects;

/**
 * Transport-neutral request for text generation.
 *
 * <p>The public text contract owns its message, tool and parameter values.
 * It does not expose execution SPI objects or transport DTOs.</p>
 */
public record TextGenerationRequest(
        String model,
        List<TextGenerationMessage> messages,
        TextGenerationParameters parameters,
        List<TextGenerationToolDefinition> tools) {

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
    }

    public TextGenerationRequest(
            String model,
            List<TextGenerationMessage> messages,
            TextGenerationParameters parameters) {
        this(model, messages, parameters, List.of());
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

package tech.kayys.aqli.sdk.capability.text;

import tech.kayys.aqli.spi.Message;

import java.util.List;
import java.util.Objects;

/**
 * Transport-neutral request for text generation.
 *
 * <p>The request contains conversation input and typed generation controls.
 * It deliberately does not expose execution SPI objects, transport DTOs, or
 * untyped parameter maps.</p>
 */
public record TextGenerationRequest(
        String model,
        List<Message> messages,
        TextGenerationParameters parameters) {

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
    }

    public static TextGenerationRequest of(String model, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        return new TextGenerationRequest(
                model,
                List.of(Message.user(prompt)),
                TextGenerationParameters.unspecified());
    }
}

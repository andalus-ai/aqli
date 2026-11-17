package tech.kayys.aqli.sdk.capability.text;

import java.util.Objects;

/**
 * Completed text-generation result independent of execution backend or wire format.
 */
public record TextGenerationResponse(
        String content,
        String model,
        int inputTokens,
        int outputTokens,
        long durationMs,
        FinishReason finishReason) {

    public TextGenerationResponse {
        Objects.requireNonNull(content, "content");
        if (inputTokens < 0 || outputTokens < 0 || durationMs < 0) {
            throw new IllegalArgumentException("token counts and duration must not be negative");
        }
        finishReason = finishReason == null ? FinishReason.STOP : finishReason;
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public enum FinishReason {
        STOP,
        LENGTH,
        TOOL_CALLS,
        CONTENT_FILTER,
        ERROR
    }
}

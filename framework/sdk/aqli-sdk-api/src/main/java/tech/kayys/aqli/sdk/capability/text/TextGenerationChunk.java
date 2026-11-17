package tech.kayys.aqli.sdk.capability.text;

import java.util.Objects;

/**
 * One ordered piece of a text-generation stream.
 */
public record TextGenerationChunk(
        long sequence,
        String delta,
        boolean terminal,
        TextGenerationResponse.FinishReason finishReason) {

    public TextGenerationChunk {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        Objects.requireNonNull(delta, "delta");
        if (!terminal && finishReason != null) {
            throw new IllegalArgumentException("finishReason is only valid on a terminal chunk");
        }
    }
}

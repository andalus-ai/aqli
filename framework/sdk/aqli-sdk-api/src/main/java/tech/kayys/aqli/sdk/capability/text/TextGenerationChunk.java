package tech.kayys.aqli.sdk.capability.text;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One ordered, transport-neutral event in a text-generation stream.
 *
 * <p>The contract preserves the information required by REST and gRPC adapters
 * without exposing backend-specific streaming types.</p>
 */
public record TextGenerationChunk(
        String requestId,
        long sequence,
        String delta,
        boolean terminal,
        TextGenerationResponse.FinishReason finishReason,
        ToolCallEvent toolCallEvent,
        String toolCallId,
        String toolName,
        String toolInputDelta,
        Usage usage,
        Instant emittedAt,
        Map<String, Object> metadata) {

    public TextGenerationChunk {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        delta = delta == null ? "" : delta;
        if (!terminal && finishReason != null) {
            throw new IllegalArgumentException("finishReason is only valid on a terminal chunk");
        }
        if (toolCallEvent == null && (toolCallId != null || toolName != null || toolInputDelta != null)) {
            throw new IllegalArgumentException("tool call fields require a toolCallEvent");
        }
        if (toolCallEvent != null && (toolCallId == null || toolCallId.isBlank())) {
            throw new IllegalArgumentException("toolCallId is required for tool call events");
        }
        if (toolCallEvent == ToolCallEvent.START && (toolName == null || toolName.isBlank())) {
            throw new IllegalArgumentException("toolName is required for a tool call start");
        }
        if (toolCallEvent == ToolCallEvent.DELTA && toolInputDelta == null) {
            throw new IllegalArgumentException("toolInputDelta is required for a tool call delta");
        }
        emittedAt = emittedAt == null ? Instant.now() : emittedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Source-compatible constructor for the original minimal text chunk. */
    public TextGenerationChunk(
            long sequence,
            String delta,
            boolean terminal,
            TextGenerationResponse.FinishReason finishReason) {
        this(null, sequence, delta, terminal, finishReason, null, null, null, null,
                null, null, Map.of());
    }

    public enum ToolCallEvent {
        START,
        DELTA,
        END
    }

    /** Usage is normally present only on the terminal event. */
    public record Usage(long inputTokens, long outputTokens, long latencyMs) {
        public Usage {
            if (inputTokens < 0 || outputTokens < 0 || latencyMs < 0) {
                throw new IllegalArgumentException("usage values must not be negative");
            }
        }
    }
}

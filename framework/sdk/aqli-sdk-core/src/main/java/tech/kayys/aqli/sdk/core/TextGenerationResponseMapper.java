package tech.kayys.aqli.sdk.core;

import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;

/**
 * Maps the internal inference SPI result into the public typed text capability.
 *
 * <p>This is the response-side counterpart to {@link TextGenerationRequestMapper}.
 * Runtime adapters should use this mapper rather than maintaining private,
 * subtly different conversions between the legacy inference SPI and typed SDK
 * contracts.</p>
 */
public final class TextGenerationResponseMapper {
    private TextGenerationResponseMapper() {
    }

    /**
     * Maps an inference result after applying the explicitly configured
     * protocol parser, when the response declares a tool-call protocol.
     */
    public static TextGenerationResponse toResponse(
            InferenceResponse response,
            tech.kayys.aqli.sdk.core.tool.ToolCallResponseParser toolCallParser) {
        java.util.Objects.requireNonNull(toolCallParser, "toolCallParser");
        return toResponse(toolCallParser.apply(response));
    }

    public static TextGenerationResponse toResponse(InferenceResponse response) {
        java.util.Objects.requireNonNull(response, "response");
        return new TextGenerationResponse(
                response.getContent(),
                response.getModel(),
                response.getInputTokens(),
                response.getOutputTokens(),
                response.getDurationMs(),
                toFinishReason(response.getFinishReason()),
                response.getToolCalls().stream()
                        .map(call -> tech.kayys.aqli.spi.tool.ToolCall.builder()
                                .id(java.util.UUID.randomUUID().toString())
                                .name(call.name())
                                .arguments(call.arguments())
                                .build())
                        .toList());
    }

    public static TextGenerationChunk toChunk(StreamingInferenceChunk chunk) {
        java.util.Objects.requireNonNull(chunk, "chunk");
        TextGenerationChunk.ToolCallEvent toolCallEvent = null;
        if (chunk.isToolCallStart()) {
            toolCallEvent = TextGenerationChunk.ToolCallEvent.START;
        } else if (chunk.isToolCallDelta()) {
            toolCallEvent = TextGenerationChunk.ToolCallEvent.DELTA;
        } else if (chunk.isToolCallEnd()) {
            toolCallEvent = TextGenerationChunk.ToolCallEvent.END;
        }

        StreamingInferenceChunk.ChunkUsage chunkUsage = chunk.usage();
        TextGenerationChunk.Usage usage = chunkUsage == null ? null
                : new TextGenerationChunk.Usage(
                        chunkUsage.inputTokens(), chunkUsage.outputTokens(), chunkUsage.latencyMs());

        return new TextGenerationChunk(
                chunk.requestId(),
                chunk.index(),
                chunk.delta(),
                chunk.finished(),
                chunk.finished() ? toFinishReason(chunk.finishReason()) : null,
                toolCallEvent,
                chunk.toolCallId(),
                chunk.toolName(),
                chunk.toolInputDelta(),
                usage,
                chunk.emittedAt(),
                chunk.metadata());
    }

    public static TextGenerationResponse.FinishReason toFinishReason(
            InferenceResponse.FinishReason finishReason) {
        if (finishReason == null) {
            return TextGenerationResponse.FinishReason.STOP;
        }
        return switch (finishReason) {
            case STOP -> TextGenerationResponse.FinishReason.STOP;
            case LENGTH -> TextGenerationResponse.FinishReason.LENGTH;
            case TOOL_CALLS -> TextGenerationResponse.FinishReason.TOOL_CALLS;
            case ERROR -> TextGenerationResponse.FinishReason.ERROR;
        };
    }

    public static TextGenerationResponse.FinishReason toFinishReason(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return TextGenerationResponse.FinishReason.STOP;
        }
        return switch (finishReason.toLowerCase(java.util.Locale.ROOT)) {
            case "length" -> TextGenerationResponse.FinishReason.LENGTH;
            case "tool_calls" -> TextGenerationResponse.FinishReason.TOOL_CALLS;
            case "content_filter" -> TextGenerationResponse.FinishReason.CONTENT_FILTER;
            case "error" -> TextGenerationResponse.FinishReason.ERROR;
            default -> TextGenerationResponse.FinishReason.STOP;
        };
    }
}

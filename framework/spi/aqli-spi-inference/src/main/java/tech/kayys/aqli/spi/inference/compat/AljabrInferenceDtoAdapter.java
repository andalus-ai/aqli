package tech.kayys.aqli.spi.inference.compat;

import java.util.List;
import java.util.Objects;

import tech.kayys.aqli.spi.compat.AljabrInferenceSpiAdapter;

/**
 * Bidirectional adapters for the legacy AQLI inference DTOs and canonical
 * Aljabr inference SPI DTOs.
 *
 * <p>These adapters are a compatibility boundary, not a second contract.
 * Native {@code MemorySegment} state is deliberately not transferred: it is
 * execution-scoped and must be attached through a backend-owned execution
 * context. Conversion fails explicitly when such state is present.
 */
public final class AljabrInferenceDtoAdapter {

    private AljabrInferenceDtoAdapter() {
    }

    public static tech.kayys.aljabr.spi.inference.InferenceRequest toAljabr(
            tech.kayys.aqli.spi.inference.InferenceRequest value) {
        Objects.requireNonNull(value, "value");
        rejectNativeContext(value.getNativeContextSegment().isPresent());

        var builder = tech.kayys.aljabr.spi.inference.InferenceRequest.builder()
                .requestId(value.getRequestId())
                .apiKey(value.getApiKey())
                .model(value.getModel())
                .messages(value.getMessages().stream().map(AljabrInferenceSpiAdapter::toAljabr).toList())
                .parameters(value.getParameters())
                .toolChoice(value.getToolChoice())
                .streaming(value.isStreaming())
                .priority(tech.kayys.aljabr.spi.inference.Priority.valueOf(value.getPriority().name()))
                .cacheBypass(value.isCacheBypass())
                .promptTokenCount(value.getPromptTokenCount())
                .metadata(value.getMetadata())
                .attachments(value.getAttachments().stream()
                        .map(AljabrInferenceDtoAdapter::toAljabr)
                        .toList());

        if (value.getTools() != null) {
            value.getTools().stream()
                    .map(AljabrInferenceSpiAdapter::toAljabr)
                    .forEach(builder::tool);
        }
        value.getPreferredProvider().ifPresent(builder::preferredProvider);
        value.getPlugin().ifPresent(builder::plugin);
        value.getTimeout().ifPresent(builder::timeout);
        value.getUserId().ifPresent(builder::userId);
        value.getSessionId().ifPresent(builder::sessionId);
        value.getTraceId().ifPresent(builder::traceId);
        if (value.hasExplicitStage()) {
            builder.inferenceStage(tech.kayys.aljabr.spi.inference.InferenceStage
                    .valueOf(value.getInferenceStage().name()));
        }
        return builder.build();
    }

    public static tech.kayys.aqli.spi.inference.InferenceRequest fromAljabr(
            tech.kayys.aljabr.spi.inference.InferenceRequest value) {
        Objects.requireNonNull(value, "value");
        rejectNativeContext(value.getNativeContextSegment().isPresent());

        var builder = tech.kayys.aqli.spi.inference.InferenceRequest.builder()
                .requestId(value.getRequestId())
                .apiKey(value.getApiKey())
                .model(value.getModel())
                .messages(value.getMessages().stream().map(AljabrInferenceSpiAdapter::fromAljabr).toList())
                .parameters(value.getParameters())
                .toolChoice(value.getToolChoice())
                .streaming(value.isStreaming())
                .priority(tech.kayys.aqli.spi.inference.Priority.valueOf(value.getPriority().name()))
                .cacheBypass(value.isCacheBypass())
                .promptTokenCount(value.getPromptTokenCount())
                .metadata(value.getMetadata())
                .attachments(value.getAttachments().stream()
                        .map(AljabrInferenceDtoAdapter::fromAljabr)
                        .toList());

        if (value.getTools() != null) {
            value.getTools().stream()
                    .map(AljabrInferenceSpiAdapter::fromAljabr)
                    .forEach(builder::tool);
        }
        value.getPreferredProvider().ifPresent(builder::preferredProvider);
        value.getPlugin().ifPresent(builder::plugin);
        value.getTimeout().ifPresent(builder::timeout);
        value.getUserId().ifPresent(builder::userId);
        value.getSessionId().ifPresent(builder::sessionId);
        value.getTraceId().ifPresent(builder::traceId);
        if (value.hasExplicitStage()) {
            builder.inferenceStage(tech.kayys.aqli.spi.inference.InferenceStage
                    .valueOf(value.getInferenceStage().name()));
        }
        return builder.build();
    }

    public static tech.kayys.aljabr.spi.inference.InferenceResponse toAljabr(
            tech.kayys.aqli.spi.inference.InferenceResponse value) {
        Objects.requireNonNull(value, "value");
        List<tech.kayys.aljabr.spi.inference.InferenceResponse.ToolCall> calls = value.getToolCalls().stream()
                .map(call -> new tech.kayys.aljabr.spi.inference.InferenceResponse.ToolCall(
                        call.name(), call.arguments()))
                .toList();
        return new tech.kayys.aljabr.spi.inference.InferenceResponse(
                value.getRequestId(), value.getContent(), value.getModel(),
                value.getTokensUsed(), value.getInputTokens(), value.getOutputTokens(),
                value.getDurationMs(), value.getTimestamp(), value.getMetadata(), calls,
                tech.kayys.aljabr.spi.inference.InferenceResponse.FinishReason
                        .valueOf(value.getFinishReason().name()),
                value.getSessionId());
    }

    public static tech.kayys.aqli.spi.inference.InferenceResponse fromAljabr(
            tech.kayys.aljabr.spi.inference.InferenceResponse value) {
        Objects.requireNonNull(value, "value");
        List<tech.kayys.aqli.spi.inference.InferenceResponse.ToolCall> calls = value.getToolCalls().stream()
                .map(call -> new tech.kayys.aqli.spi.inference.InferenceResponse.ToolCall(
                        call.name(), call.arguments()))
                .toList();
        return new tech.kayys.aqli.spi.inference.InferenceResponse(
                value.getRequestId(), value.getContent(), value.getModel(),
                value.getTokensUsed(), value.getInputTokens(), value.getOutputTokens(),
                value.getDurationMs(), value.getTimestamp(), value.getMetadata(), calls,
                tech.kayys.aqli.spi.inference.InferenceResponse.FinishReason
                        .valueOf(value.getFinishReason().name()),
                value.getSessionId());
    }

    public static tech.kayys.aljabr.spi.inference.StreamingInferenceChunk toAljabr(
            tech.kayys.aqli.spi.inference.StreamingInferenceChunk value) {
        Objects.requireNonNull(value, "value");
        var usage = value.usage() == null ? null
                : new tech.kayys.aljabr.spi.inference.StreamingInferenceChunk.ChunkUsage(
                        value.usage().inputTokens(), value.usage().outputTokens(), value.usage().latencyMs());
        return new tech.kayys.aljabr.spi.inference.StreamingInferenceChunk(
                value.requestId(), value.index(), value.modality(), value.delta(), value.imageDeltaBase64(),
                value.finished(), value.finishReason(), usage, value.emittedAt(), value.metadata(),
                value.toolCallId(), value.toolName(), value.toolInputDelta());
    }

    public static tech.kayys.aqli.spi.inference.StreamingInferenceChunk fromAljabr(
            tech.kayys.aljabr.spi.inference.StreamingInferenceChunk value) {
        Objects.requireNonNull(value, "value");
        var usage = value.usage() == null ? null
                : new tech.kayys.aqli.spi.inference.StreamingInferenceChunk.ChunkUsage(
                        value.usage().inputTokens(), value.usage().outputTokens(), value.usage().latencyMs());
        return new tech.kayys.aqli.spi.inference.StreamingInferenceChunk(
                value.requestId(), value.index(), value.modality(), value.delta(), value.imageDeltaBase64(),
                value.finished(), value.finishReason(), usage, value.emittedAt(), value.metadata(),
                value.toolCallId(), value.toolName(), value.toolInputDelta());
    }

    private static tech.kayys.aljabr.spi.inference.Attachment toAljabr(
            tech.kayys.aqli.spi.inference.Attachment value) {
        return new tech.kayys.aljabr.spi.inference.Attachment(
                value.getId(),
                tech.kayys.aljabr.spi.inference.Attachment.Type.valueOf(value.getType().name()),
                value.getMimeType(), value.getUrl(), value.getBase64Data(), value.getMetadata());
    }

    private static tech.kayys.aqli.spi.inference.Attachment fromAljabr(
            tech.kayys.aljabr.spi.inference.Attachment value) {
        return new tech.kayys.aqli.spi.inference.Attachment(
                value.getId(),
                tech.kayys.aqli.spi.inference.Attachment.Type.valueOf(value.getType().name()),
                value.getMimeType(), value.getUrl(), value.getBase64Data(), value.getMetadata());
    }

    private static void rejectNativeContext(boolean present) {
        if (present) {
            throw new IllegalArgumentException(
                    "Cannot convert an inference request carrying native MemorySegment state; "
                    + "move it to an execution-scoped backend context first");
        }
    }
}

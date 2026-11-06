package tech.kayys.aqli.spi.inference.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;

class AljabrInferenceDtoAdapterTest {

    @Test
    void roundTripsRequestAndPreservesPortableFields() {
        InferenceRequest request = InferenceRequest.builder()
                .requestId("request-1")
                .apiKey("test-key")
                .model("test-model")
                .message(Message.user("hello"))
                .parameter("temperature", 0.4)
                .parameter("max_tokens", 64)
                .priority(tech.kayys.aqli.spi.inference.Priority.HIGH)
                .preferredProvider("metal")
                .plugin("test-plugin")
                .timeout(Duration.ofSeconds(10))
                .userId("user-1")
                .sessionId("session-1")
                .traceId("trace-1")
                .inferenceStage(tech.kayys.aqli.spi.inference.InferenceStage.PREFILL)
                .promptTokenCount(3)
                .metadata("origin", "test")
                .attachment(tech.kayys.aqli.spi.inference.Attachment.fromUrl(
                        "https://example.test/image.png", "image/png"))
                .build();

        var canonical = AljabrInferenceDtoAdapter.toAljabr(request);
        var restored = AljabrInferenceDtoAdapter.fromAljabr(canonical);

        assertEquals(request.getRequestId(), restored.getRequestId());
        assertEquals(request.getApiKey(), restored.getApiKey());
        assertEquals(request.getModel(), restored.getModel());
        assertEquals(request.getMessages(), restored.getMessages());
        assertEquals(request.getParameters(), restored.getParameters());
        assertEquals(request.getPriority(), restored.getPriority());
        assertEquals(request.getPreferredProvider(), restored.getPreferredProvider());
        assertEquals(request.getPlugin(), restored.getPlugin());
        assertEquals(request.getTimeout(), restored.getTimeout());
        assertEquals(request.getUserId(), restored.getUserId());
        assertEquals(request.getSessionId(), restored.getSessionId());
        assertEquals(request.getTraceId(), restored.getTraceId());
        assertEquals(request.getInferenceStage(), restored.getInferenceStage());
        assertEquals(request.getPromptTokenCount(), restored.getPromptTokenCount());
        assertEquals(request.getMetadata(), restored.getMetadata());
        assertEquals(request.getAttachments().get(0).getUrl(), restored.getAttachments().get(0).getUrl());
    }

    @Test
    void roundTripsResponseAndToolCalls() {
        InferenceResponse response = InferenceResponse.builder()
                .requestId("request-2")
                .content("done")
                .model("test-model")
                .inputTokens(12)
                .outputTokens(4)
                .tokensUsed(16)
                .durationMs(250)
                .timestamp(Instant.parse("2026-11-06T00:00:00Z"))
                .metadata("provider", "java")
                .toolCall(new InferenceResponse.ToolCall("lookup", Map.of("q", "x")))
                .finishReason(InferenceResponse.FinishReason.TOOL_CALLS)
                .sessionId("session-2")
                .build();

        var restored = AljabrInferenceDtoAdapter.fromAljabr(
                AljabrInferenceDtoAdapter.toAljabr(response));

        assertEquals(response.getRequestId(), restored.getRequestId());
        assertEquals(response.getContent(), restored.getContent());
        assertEquals(response.getModel(), restored.getModel());
        assertEquals(response.getTokensUsed(), restored.getTokensUsed());
        assertEquals(response.getInputTokens(), restored.getInputTokens());
        assertEquals(response.getOutputTokens(), restored.getOutputTokens());
        assertEquals(response.getDurationMs(), restored.getDurationMs());
        assertEquals(response.getTimestamp(), restored.getTimestamp());
        assertEquals(response.getMetadata(), restored.getMetadata());
        assertEquals(response.getToolCalls(), restored.getToolCalls());
        assertEquals(response.getFinishReason(), restored.getFinishReason());
        assertEquals(response.getSessionId(), restored.getSessionId());
    }

    @Test
    void roundTripsStreamingToolCallAndUsageFields() {
        StreamingInferenceChunk chunk = new StreamingInferenceChunk(
                "request-3", 7, tech.kayys.aljabr.spi.model.ModalityType.TEXT,
                null, null, false, null,
                new StreamingInferenceChunk.ChunkUsage(10, 2, 31),
                Instant.parse("2026-11-06T00:00:01Z"),
                Map.of("provider", "java"), "call-1", "lookup", "{\"q\":");

        var restored = AljabrInferenceDtoAdapter.fromAljabr(
                AljabrInferenceDtoAdapter.toAljabr(chunk));

        assertEquals(chunk.requestId(), restored.requestId());
        assertEquals(chunk.index(), restored.index());
        assertEquals(chunk.modality(), restored.modality());
        assertEquals(chunk.finished(), restored.finished());
        assertEquals(chunk.usage(), restored.usage());
        assertEquals(chunk.emittedAt(), restored.emittedAt());
        assertEquals(chunk.metadata(), restored.metadata());
        assertEquals(chunk.toolCallId(), restored.toolCallId());
        assertEquals(chunk.toolName(), restored.toolName());
        assertEquals(chunk.toolInputDelta(), restored.toolInputDelta());
    }
}

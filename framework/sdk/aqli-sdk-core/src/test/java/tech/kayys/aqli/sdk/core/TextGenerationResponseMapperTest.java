package tech.kayys.aqli.sdk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aljabr.spi.model.ModalityType;

class TextGenerationResponseMapperTest {

    @Test
    void mapsInferenceResponseFieldsAndToolCallsToTypedResponse() {
        InferenceResponse source = InferenceResponse.builder()
                .requestId("response-1")
                .model("model-id")
                .content("done")
                .inputTokens(7)
                .outputTokens(3)
                .durationMs(42)
                .finishReason(InferenceResponse.FinishReason.TOOL_CALLS)
                .toolCall(new InferenceResponse.ToolCall("lookup", Map.of("query", "weather")))
                .build();

        TextGenerationResponse mapped = TextGenerationResponseMapper.toResponse(source);

        assertEquals("done", mapped.content());
        assertEquals("model-id", mapped.model());
        assertEquals(7, mapped.inputTokens());
        assertEquals(3, mapped.outputTokens());
        assertEquals(42, mapped.durationMs());
        assertEquals(TextGenerationResponse.FinishReason.TOOL_CALLS, mapped.finishReason());
        assertEquals(1, mapped.toolCalls().size());
        assertNotNull(mapped.toolCalls().getFirst().getId());
        assertEquals("lookup", mapped.toolCalls().getFirst().getFunction().getName());
        assertEquals(Map.of("query", "weather"), mapped.toolCalls().getFirst().getArguments());
    }

    @Test
    void mapsStreamingChunkUsageMetadataAndTerminalReason() {
        Instant emittedAt = Instant.parse("2026-11-19T03:00:00Z");
        StreamingInferenceChunk source = new StreamingInferenceChunk(
                "stream-1", 9, ModalityType.TEXT, "hello", null, true, "length",
                new StreamingInferenceChunk.ChunkUsage(5, 2, 18),
                emittedAt, Map.of("provider", "cpu"), null, null, null);

        var mapped = TextGenerationResponseMapper.toChunk(source);

        assertEquals("stream-1", mapped.requestId());
        assertEquals(9, mapped.sequence());
        assertEquals("hello", mapped.delta());
        assertEquals(true, mapped.terminal());
        assertEquals(TextGenerationResponse.FinishReason.LENGTH, mapped.finishReason());
        assertEquals(new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk.Usage(5, 2, 18),
                mapped.usage());
        assertEquals(emittedAt, mapped.emittedAt());
        assertEquals(Map.of("provider", "cpu"), mapped.metadata());
    }

    @Test
    void mapsContentFilterFinishReasonWithoutCollapsingItToStop() {
        assertEquals(TextGenerationResponse.FinishReason.CONTENT_FILTER,
                TextGenerationResponseMapper.toFinishReason("content_filter"));
    }
}

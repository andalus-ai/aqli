package tech.kayys.aqli.safetensor.engine.warmup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceStage;
import tech.kayys.aqli.spi.inference.Priority;

class SafetensorInferenceEngineAdapterTest {

    @Test
    void translatesTextRequestAndPreservesRuntimeContext() {
        InferenceRequest request = InferenceRequest.builder()
                .requestId("request-1")
                .apiKey("community-key")
                .model("/models/gemma")
                .message(Message.system("Be concise"))
                .message(Message.user("Hello"))
                .temperature(0.4)
                .topK(20)
                .maxTokens(64)
                .streaming(true)
                .priority(Priority.HIGH)
                .userId("user-1")
                .sessionId("session-1")
                .traceId("trace-1")
                .inferenceStage(InferenceStage.COMBINED)
                .metadata("tenantId", "tenant-1")
                .build();

        tech.kayys.aljabr.spi.inference.InferenceRequest translated =
                SafetensorInferenceEngineAdapter.toAljabr(request);

        assertEquals("request-1", translated.getRequestId());
        assertEquals("/models/gemma", translated.getModel());
        assertEquals("community-key", translated.getApiKey());
        assertEquals(2, translated.getMessages().size());
        assertEquals("Be concise", translated.getMessages().get(0).getContent());
        assertEquals("Hello", translated.getMessages().get(1).getContent());
        assertEquals(0.4d, translated.getTemperature());
        assertEquals(20, translated.getTopK());
        assertEquals(64, translated.getMaxTokens());
        assertEquals(true, translated.isStreaming());
        assertEquals(tech.kayys.aljabr.spi.inference.Priority.HIGH, translated.getPriority());
        assertEquals("user-1", translated.getUserId().orElseThrow());
        assertEquals("session-1", translated.getSessionId().orElseThrow());
        assertEquals("trace-1", translated.getTraceId().orElseThrow());
        assertEquals("tenant-1", translated.getMetadata().get("tenantId"));
    }

    @Test
    void rejectsToolRequestsInsteadOfSilentlyDroppingThem() {
        InferenceRequest request = InferenceRequest.builder()
                .model("/models/gemma")
                .message(Message.user("Call a tool"))
                .toolChoice("required")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> SafetensorInferenceEngineAdapter.toAljabr(request));
    }

    @Test
    void rejectsMultimodalPayloadsInsteadOfTreatingThemAsText() {
        InferenceRequest request = InferenceRequest.builder()
                .model("/models/gemma")
                .message(Message.multiModal(Message.Role.USER,
                        java.util.List.of(Message.ContentPart.imageUrl("https://example.invalid/image.png"))))
                .build();

        // The SPI payload can be translated structurally, but this text-only backend
        // does not claim to execute image input. Reject before handing it to generation.
        assertThrows(UnsupportedOperationException.class,
                () -> SafetensorInferenceEngineAdapter.toAljabr(request));
    }
}

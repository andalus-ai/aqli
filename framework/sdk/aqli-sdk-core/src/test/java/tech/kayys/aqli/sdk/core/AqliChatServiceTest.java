package tech.kayys.aqli.sdk.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AqliChatServiceTest {

    private final AqliChatService service = new AqliChatService();

    @Test
    void createChatRequestPreservesPreferredProvider() {
        ChatParams params = new ChatParams(0.2, 256, 0.9, 1.1, "safetensor");

        var request = service.createRequest(
                "model-id",
                null,
                List.of(tech.kayys.aqli.spi.Message.user("hello")),
                List.of(),
                params,
                false);

        assertEquals("safetensor", request.getPreferredProvider().orElseThrow());
        assertEquals(0.2, request.getTemperature(), 0.0001);
        assertEquals(256, request.getMaxTokens());
        assertEquals(0.9, request.getTopP(), 0.0001);
        assertEquals(1.1, request.getRepeatPenalty(), 0.0001);
    }

    @Test
    void createsCanonicalTypedTextRequestWithSharedChatDefaultsAndTools() {
        ToolDefinition tool = ToolDefinition.builder().name("lookup").build();
        ChatParams params = new ChatParams(0.2, 256, 0.9, 1.1, "safetensor");

        var request = service.createTextGenerationRequest(
                "model-id",
                "be concise",
                List.of(Message.user("hello")),
                List.of(tool),
                params);

        assertEquals("model-id", request.model());
        assertEquals(List.of(Message.system("be concise"), Message.user("hello")), request.messages());
        assertEquals(List.of(tool), request.tools());
        assertEquals(0.2, request.parameters().temperature(), 0.0001);
        assertEquals(0.9, request.parameters().topP(), 0.0001);
        assertEquals(40, request.parameters().topK());
        assertEquals(256, request.parameters().maxTokens());
        assertEquals(1.1, request.parameters().repeatPenalty(), 0.0001);
        assertEquals(false, request.parameters().jsonMode());
    }

    @Test
    void treatsNonPositiveChatTokenLimitAsUnspecified() {
        var request = service.createTextGenerationRequest(
                "model-id",
                null,
                List.of(Message.user("hello")),
                List.of(),
                ChatParams.of(0.0, 0));

        assertEquals(null, request.parameters().maxTokens());
    }

    @Test
    void typedTextStreamPreservesSdkEventMetadataAndToolCallFields() {
        var request = tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.of("model-id", "hello");
        var emittedAt = java.time.Instant.parse("2026-11-18T02:00:00Z");
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var sourceChunk = new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk(
                "stream-request", 4, "", false, null,
                tech.kayys.aqli.sdk.capability.text.TextGenerationChunk.ToolCallEvent.DELTA,
                "call-1", null, "{\\"query\\":",
                new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk.Usage(5, 2, 18),
                emittedAt, java.util.Map.of("provider", "cpu"));
        org.mockito.Mockito.when(sdk.streamText(request))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().item(sourceChunk));

        var chunks = service.streamText(sdk, request).collect().asList().await().indefinitely();

        assertEquals(1, chunks.size());
        var chunk = chunks.getFirst();
        assertEquals("stream-request", chunk.requestId());
        assertEquals(4, chunk.index());
        assertEquals("call-1", chunk.toolCallId());
        assertEquals("{\\"query\\":", chunk.toolInputDelta());
        assertEquals(new tech.kayys.aqli.spi.inference.StreamingInferenceChunk.ChunkUsage(5, 2, 18),
                chunk.usage());
        assertEquals(emittedAt, chunk.emittedAt());
        assertEquals(java.util.Map.of("provider", "cpu"), chunk.metadata());
    }

    @Test
    void createCompletionRequestPreservesPreferredProvider() {
        ChatParams params = new ChatParams(0.2, 256, 0.9, 1.1, "llamacpp");

        var request = service.createCompletionRequest("model-id", "hello", params, false);

        assertEquals("llamacpp", request.getPreferredProvider().orElseThrow());
        assertEquals(0.2, request.getTemperature(), 0.0001);
        assertEquals(256, request.getMaxTokens());
        assertEquals(0.9, request.getTopP(), 0.0001);
        assertEquals(1.1, request.getRepeatPenalty(), 0.0001);
    }

    @Test
    void typedTextExecutionPreservesCallerRequestId() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var request = new tech.kayys.aqli.sdk.capability.text.TextGenerationRequest(
                "model-id",
                List.of(tech.kayys.aqli.sdk.capability.text.TextGenerationMessage.user("hello")),
                tech.kayys.aqli.sdk.capability.text.TextGenerationParameters.unspecified(),
                List.of(), null, java.util.Map.of(),
                "caller-request-42", "user-1", "session-1", "trace-1");
        var response = new tech.kayys.aqli.sdk.capability.text.TextGenerationResponse(
                "hello", "model-id", 1, 1, 10,
                tech.kayys.aqli.sdk.capability.text.TextGenerationResponse.FinishReason.STOP);
        org.mockito.Mockito.when(sdk.generateTextAsync(request))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));

        service.generateText(sdk, request).await().indefinitely();

        var captor = org.mockito.ArgumentCaptor.forClass(
                tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.class);
        org.mockito.Mockito.verify(sdk).generateTextAsync(captor.capture());
        assertEquals("caller-request-42", captor.getValue().requestId());
        assertEquals("user-1", captor.getValue().userId());
        assertEquals("session-1", captor.getValue().sessionId());
        assertEquals("trace-1", captor.getValue().traceId());
    }

    @Test
    void typedTextStreamingAssignsRequestIdWhenCallerOmitsIt() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var request = tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.of("model-id", "hello");
        org.mockito.Mockito.when(sdk.streamText(request))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().empty());

        service.streamText(sdk, request).collect().asList().await().indefinitely();

        var captor = org.mockito.ArgumentCaptor.forClass(
                tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.class);
        org.mockito.Mockito.verify(sdk).streamText(captor.capture());
        assertEquals(false, captor.getValue().requestId() == null
                || captor.getValue().requestId().isBlank());
    }
}

package tech.kayys.aqli.protobuf;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.inference.InferenceRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GrpcInferenceRequestMapperTest {

    private final GrpcInferenceRequestMapper mapper = new GrpcInferenceRequestMapper();

    @Test
    void mapsChatWireRequestToSharedSdkRequest() {
        ChatRequest grpcRequest = ChatRequest.newBuilder()
                .setModelId("model-id")
                .addMessages(Message.newBuilder().setRole("system").setContent("Be concise"))
                .addMessages(Message.newBuilder().setRole("user").setContent("hello"))
                .setTemperature(0.25f)
                .setMaxTokens(128)
                .build();

        InferenceRequest request = mapper.toChatRequest(grpcRequest, false);

        assertEquals("model-id", request.getModel());
        assertEquals(2, request.getMessages().size());
        assertEquals(tech.kayys.aqli.spi.Message.Role.SYSTEM, request.getMessages().get(0).getRole());
        assertEquals("Be concise", request.getMessages().get(0).getContent());
        assertEquals(tech.kayys.aqli.spi.Message.Role.USER, request.getMessages().get(1).role());
        assertEquals("hello", request.getMessages().get(1).content());
        assertEquals(0.25, request.getTemperature(), 0.0001);
        assertEquals(128, request.getMaxTokens());
        assertEquals(0.9, request.getTopP(), 0.0001);
        assertEquals(1.1, request.getRepeatPenalty(), 0.0001);
        assertEquals(40, request.getTopK());
        assertFalse(request.isStreaming());
    }

    @Test
    void mapsStreamingChatUsingSameRequestSemantics() {
        ChatRequest grpcRequest = ChatRequest.newBuilder()
                .setModelId("model-id")
                .addMessages(Message.newBuilder().setRole("user").setContent("hello"))
                .setTemperature(0.4f)
                .setMaxTokens(64)
                .build();

        InferenceRequest request = mapper.toChatRequest(grpcRequest, true);

        assertEquals("model-id", request.getModel());
        assertEquals("hello", request.getMessages().get(0).content());
        assertEquals(0.4, request.getTemperature(), 0.0001);
        assertEquals(64, request.getMaxTokens());
        assertTrue(request.isStreaming());
    }

    @Test
    void mapsCompletionWireRequestToSharedSdkRequest() {
        CompletionRequest grpcRequest = CompletionRequest.newBuilder()
                .setModelId("model-id")
                .setPrompt("continue this")
                .setTemperature(0.3f)
                .setMaxTokens(96)
                .build();

        InferenceRequest request = mapper.toCompletionRequest(grpcRequest, false);

        assertEquals("model-id", request.getModel());
        assertEquals("continue this", request.getPrompt());
        assertEquals("continue this", request.getMessages().get(0).getContent());
        assertEquals(0.3, request.getTemperature(), 0.0001);
        assertEquals(96, request.getMaxTokens());
        assertEquals(0.9, request.getTopP(), 0.0001);
        assertEquals(1.1, request.getRepeatPenalty(), 0.0001);
        assertEquals(40, request.getTopK());
        assertTrue(!request.isStreaming());
    }

    @Test
    void mapsStreamingCompletionUsingSameRequestSemantics() {
        CompletionRequest grpcRequest = CompletionRequest.newBuilder()
                .setModelId("model-id")
                .setPrompt("continue this")
                .setTemperature(0.6f)
                .setMaxTokens(32)
                .build();

        InferenceRequest request = mapper.toCompletionRequest(grpcRequest, true);

        assertEquals("model-id", request.getModel());
        assertEquals("continue this", request.getPrompt());
        assertEquals(0.6, request.getTemperature(), 0.0001);
        assertEquals(32, request.getMaxTokens());
        assertTrue(request.isStreaming());
    }
}

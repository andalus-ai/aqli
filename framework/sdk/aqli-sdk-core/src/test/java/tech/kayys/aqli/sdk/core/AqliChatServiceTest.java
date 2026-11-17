package tech.kayys.aqli.sdk.core;

import org.junit.jupiter.api.Test;

import java.util.List;

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
    void createCompletionRequestPreservesPreferredProvider() {
        ChatParams params = new ChatParams(0.2, 256, 0.9, 1.1, "llamacpp");

        var request = service.createCompletionRequest("model-id", "hello", params, false);

        assertEquals("llamacpp", request.getPreferredProvider().orElseThrow());
        assertEquals(0.2, request.getTemperature(), 0.0001);
        assertEquals(256, request.getMaxTokens());
        assertEquals(0.9, request.getTopP(), 0.0001);
        assertEquals(1.1, request.getRepeatPenalty(), 0.0001);
    }
}

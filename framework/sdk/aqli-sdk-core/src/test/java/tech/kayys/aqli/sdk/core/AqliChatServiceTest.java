package tech.kayys.aqli.sdk.core;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.Message;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AqliChatServiceTest {

    private final AqliChatService service = new AqliChatService();

    @Test
    void createChatRequestUsesSharedTextGenerationMappingAndPreservesProvider() {
        ChatParams params = new ChatParams(0.2, 256, 0.85, 1.15, "safetensor");

        var request = service.createRequest(
                "model-id",
                null,
                List.of(Message.user("hello")),
                List.of(),
                params,
                false);

        assertEquals("safetensor", request.getPreferredProvider().orElseThrow());
        assertEquals(0.2, request.getTemperature(), 0.0001);
        assertEquals(0.85, request.getTopP(), 0.0001);
        assertEquals(1.15, request.getRepeatPenalty(), 0.0001);
        assertEquals(256, request.getMaxTokens());
        assertEquals(40, request.getTopK());
        assertFalse(request.isJsonMode());
    }

    @Test
    void createCompletionRequestUsesSharedTextGenerationMappingAndPreservesProvider() {
        ChatParams params = new ChatParams(0.35, 512, 0.9, 1.05, "llamacpp");

        var request = service.createCompletionRequest("model-id", "hello", params, true);

        assertEquals("llamacpp", request.getPreferredProvider().orElseThrow());
        assertEquals(0.35, request.getTemperature(), 0.0001);
        assertEquals(0.9, request.getTopP(), 0.0001);
        assertEquals(1.05, request.getRepeatPenalty(), 0.0001);
        assertEquals(512, request.getMaxTokens());
        assertEquals("hello", request.getPrompt());
        assertEquals("hello", request.getMessages().get(0).getContent());
    }
}

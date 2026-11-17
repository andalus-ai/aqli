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

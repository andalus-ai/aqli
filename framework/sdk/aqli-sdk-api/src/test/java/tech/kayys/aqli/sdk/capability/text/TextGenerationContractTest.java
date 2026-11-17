package tech.kayys.aqli.sdk.capability.text;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextGenerationContractTest {

    @Test
    void requestCopiesMessagesAndUsesUnspecifiedParametersByDefault() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("hello"));

        TextGenerationRequest request = new TextGenerationRequest(" model-a ", messages, null);
        messages.clear();

        assertEquals("model-a", request.model());
        assertEquals(List.of(Message.user("hello")), request.messages());
        assertEquals(TextGenerationParameters.unspecified(), request.parameters());
        assertEquals(List.of(), request.tools());
        assertThrows(UnsupportedOperationException.class,
                () -> request.messages().add(Message.user("mutate")));
    }

    @Test
    void requestCopiesToolDefinitionsAndRetainsLegacyConstructor() {
        ToolDefinition tool = ToolDefinition.builder().name("lookup").build();
        List<ToolDefinition> tools = new ArrayList<>(List.of(tool));

        TextGenerationRequest request = new TextGenerationRequest(
                "model-a", List.of(Message.user("find this")), null, tools);
        tools.clear();

        assertEquals(List.of(tool), request.tools());
        assertThrows(UnsupportedOperationException.class,
                () -> request.tools().add(ToolDefinition.builder().name("other").build()));

        TextGenerationRequest legacy = new TextGenerationRequest(
                "model-a", List.of(Message.user("hello")), null);
        assertEquals(List.of(), legacy.tools());
    }

    @Test
    void promptFactoryCreatesUserMessage() {
        TextGenerationRequest request = TextGenerationRequest.of("model-a", "hello");

        assertEquals("model-a", request.model());
        assertEquals(Message.Role.USER, request.messages().getFirst().getRole());
        assertEquals("hello", request.messages().getFirst().getContent());
    }

    @Test
    void rejectsInvalidRequestAndGenerationControls() {
        assertThrows(IllegalArgumentException.class,
                () -> TextGenerationRequest.of("model-a", " "));
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationRequest(" ", List.of(Message.user("hello")), null));
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationParameters(2.1, null, null, null,
                        null, null, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationParameters(null, 0.0, null, null,
                        null, null, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationParameters(null, null, null, 0,
                        null, null, null, null, null, List.of()));
    }

    @Test
    void responseCalculatesTotalTokensAndDefaultsFinishReason() {
        TextGenerationResponse response =
                new TextGenerationResponse("hello", "model-a", 3, 2, 10, null);

        assertEquals(5, response.totalTokens());
        assertEquals(TextGenerationResponse.FinishReason.STOP, response.finishReason());
    }

    @Test
    void chunkRequiresNonNegativeSequenceAndOnlyTerminalChunksCarryFinishReason() {
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationChunk(-1, "hello", false, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationChunk(0, "hello", false,
                        TextGenerationResponse.FinishReason.STOP));
    }
}

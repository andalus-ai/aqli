package tech.kayys.aqli.sdk.capability.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextGenerationContractTest {

    @Test
    void requestCopiesMessagesAndUsesUnspecifiedParametersByDefault() {
        List<TextGenerationMessage> messages = new ArrayList<>();
        messages.add(TextGenerationMessage.user("hello"));

        TextGenerationRequest request = new TextGenerationRequest(" model-a ", messages, null);
        messages.clear();

        assertEquals("model-a", request.model());
        assertEquals(List.of(TextGenerationMessage.user("hello")), request.messages());
        assertEquals(TextGenerationParameters.unspecified(), request.parameters());
        assertThrows(UnsupportedOperationException.class,
                () -> request.messages().add(TextGenerationMessage.user("mutate")));
    }

    @Test
    void promptFactoryCreatesUserMessage() {
        TextGenerationRequest request = TextGenerationRequest.of("model-a", "hello");

        assertEquals("model-a", request.model());
        assertEquals(TextGenerationMessage.Role.USER, request.messages().getFirst().role());
        assertEquals("hello", request.messages().getFirst().content());
    }

    @Test
    void textMessagesRetainToolResultCorrelationWithoutExecutionSpiTypes() {
        TextGenerationMessage toolResult = TextGenerationMessage.tool("call-7", "result");

        assertEquals(TextGenerationMessage.Role.TOOL, toolResult.role());
        assertEquals("call-7", toolResult.toolCallId());
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationMessage(TextGenerationMessage.Role.USER, "hello", null, "call-7"));
    }

    @Test
    void rejectsInvalidRequestAndGenerationControls() {
        assertThrows(IllegalArgumentException.class,
                () -> TextGenerationRequest.of("model-a", " "));
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationRequest(" ", List.of(TextGenerationMessage.user("hello")), null));
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

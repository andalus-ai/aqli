package tech.kayys.aqli.sdk.core;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.sdk.capability.text.SdkMessage;
import tech.kayys.aqli.sdk.capability.text.TextGenerationParameters;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextGenerationRequestMapperTest {

    @Test
    void mapsCommonAndOptionalTextGenerationControls() {
        InferenceRequest request = InferenceRequest.builder()
                .requestId("request-1")
                .model("model-1")
                .build();

        InferenceRequest.Builder builder = request.toBuilder();
        TextGenerationRequestMapper.apply(builder, new TextGenerationOptions(
                0.35, 0.82, 32, 1.15, true, 512, 2, "root ::= \"ok\""));

        InferenceRequest mapped = builder.build();
        assertEquals(0.35, mapped.getTemperature(), 0.0001);
        assertEquals(0.82, mapped.getTopP(), 0.0001);
        assertEquals(32, mapped.getTopK());
        assertEquals(1.15, mapped.getRepeatPenalty(), 0.0001);
        assertTrue(mapped.isJsonMode());
        assertEquals(512, mapped.getMaxTokens());
        assertEquals(2, mapped.getMirostat());
        assertEquals("root ::= \"ok\"", mapped.getGrammar());
    }

    @Test
    void mapsSdkOwnedConversationMessagesIntoExecutionSpi() {
        SdkMessage assistant = new SdkMessage(
                SdkMessage.Role.ASSISTANT,
                null,
                List.of(),
                "assistant",
                List.of(new SdkMessage.ToolCall(
                        "call-1",
                        "lookup",
                        SdkMessage.ToolType.FUNCTION,
                        new SdkMessage.FunctionCall("lookup", "{\"q\":\"aqli\"}"),
                        Map.of("q", "aqli"))),
                null);
        SdkMessage multimodal = SdkMessage.multimodal(
                SdkMessage.Role.USER,
                List.of(
                        SdkMessage.ContentPart.text("describe"),
                        SdkMessage.ContentPart.imageUrl("https://example.test/image.png")));

        TextGenerationRequest publicRequest = new TextGenerationRequest(
                "model-typed",
                List.of(SdkMessage.system("be concise"), SdkMessage.user("hello"), assistant, multimodal),
                TextGenerationParameters.unspecified());

        InferenceRequest mapped = TextGenerationRequestMapper.toInferenceRequest(publicRequest);
        List<Message> messages = mapped.getMessages();

        assertEquals(4, messages.size());
        assertEquals(Message.Role.SYSTEM, messages.get(0).getRole());
        assertEquals("hello", messages.get(1).getContent());
        assertEquals("assistant", messages.get(2).getName());
        assertEquals("call-1", messages.get(2).getToolCalls().get(0).getId());
        assertEquals("lookup", messages.get(2).getToolCalls().get(0).getFunction().getName());
        assertEquals(Map.of("q", "aqli"), messages.get(2).getToolCalls().get(0).getArguments());
        assertTrue(messages.get(3).isMultiModal());
        assertEquals(Message.ContentType.IMAGE_URL, messages.get(3).getContentParts().get(1).type());
    }

    @Test
    void typedStreamingRequestMarksTheExecutionRequestAsStreaming() {
        InferenceRequest mapped = TextGenerationRequestMapper.toStreamingInferenceRequest(
                TextGenerationRequest.of("model-typed", "hello"));

        assertTrue(mapped.isStreaming());
    }

    @Test
    void leavesOptionalControlsUnsetWhenNotRequested() {
        InferenceRequest.Builder builder = InferenceRequest.builder()
                .requestId("request-2")
                .model("model-2");

        TextGenerationRequestMapper.apply(builder, new TextGenerationOptions(
                0.2, 0.9, 40, 1.1, false, 128, 0, null));

        InferenceRequest mapped = builder.build();
        assertFalse(mapped.isJsonMode());
        assertEquals(0, mapped.getMirostat());
        assertEquals(null, mapped.getGrammar());
    }
}

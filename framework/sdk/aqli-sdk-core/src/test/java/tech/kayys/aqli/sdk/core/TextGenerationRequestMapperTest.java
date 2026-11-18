package tech.kayys.aqli.sdk.core;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.sdk.capability.text.SdkMessage;
import tech.kayys.aqli.sdk.capability.text.TextGenerationParameters;
import tech.kayys.aqli.sdk.capability.text.TextGenerationContext;
import tech.kayys.aqli.sdk.capability.text.TextGenerationContextItem;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.sdk.capability.text.TextGenerationMessage;
import tech.kayys.aqli.sdk.capability.text.TextGenerationToolCall;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;

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

        assertEquals("model-typed", mapped.getModel());
        assertEquals(publicRequest.messages(), mapped.getMessages());
        assertEquals(java.util.List.of(tool), mapped.getTools());
        assertEquals(0.35, mapped.getTemperature(), 0.0001);
        assertEquals(0.82, mapped.getTopP(), 0.0001);
        assertEquals(24, mapped.getTopK());
        assertEquals(96, mapped.getMaxTokens());
        assertEquals(1.12, mapped.getRepeatPenalty(), 0.0001);
        assertEquals(0.04, ((Number) mapped.getParameters().get("min_p")).doubleValue(), 0.0001);
        assertEquals(7, mapped.getParameters().get("seed"));
        assertTrue(mapped.isJsonMode());
        assertEquals("root ::= \"ok\"", mapped.getGrammar());
        assertEquals(java.util.List.of("END"), mapped.getParameters().get("stop"));
        assertFalse(mapped.isStreaming());
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
    @Test
    void mapsToolCallsAndAgenticContextIntoExecutionRequest() {
        TextGenerationMessage assistant = new TextGenerationMessage(
                TextGenerationMessage.Role.ASSISTANT,
                "",
                null,
                null,
                List.of(new TextGenerationToolCall("call-7", "search", TextGenerationToolCall.Type.FUNCTION,
                        Map.of("query", "SDK parity"))));
        TextGenerationRequest typed = new TextGenerationRequest(
                "model-agent",
                List.of(TextGenerationMessage.user("search this"), assistant),
                TextGenerationParameters.unspecified(),
                List.of(),
                Map.of("type", "function", "function", Map.of("name", "search")),
                Map.of("rag_context_injected", true, "trace_marker", "trace"),
                "request-7",
                "user-7",
                "session-7",
                "trace-7");

        InferenceRequest mapped = TextGenerationRequestMapper.toInferenceRequest(typed);
        Message mappedAssistant = mapped.getMessages().get(1);

        assertEquals("request-7", mapped.getRequestId());
        assertEquals("user-7", mapped.getUserId().orElseThrow());
        assertEquals("session-7", mapped.getSessionId().orElseThrow());
        assertEquals("trace-7", mapped.getTraceId().orElseThrow());
        assertEquals(typed.toolChoice(), mapped.getToolChoice());
        assertEquals(typed.metadata(), mapped.getMetadata());
        assertEquals(1, mappedAssistant.getToolCalls().size());
        assertEquals("call-7", mappedAssistant.getToolCalls().get(0).getId());
        assertEquals("search", mappedAssistant.getToolCalls().get(0).getFunction().getName());
        assertEquals("SDK parity", mappedAssistant.getToolCalls().get(0).getArguments().get("query"));
    }

    @Test
    void mapsTypedRetrievalContextIntoExecutionRequest() {
        TextGenerationContext context = new TextGenerationContext(
                true,
                "embedding-model-v2",
                List.of(new TextGenerationContextItem(
                        "AQLI exposes a transport-neutral text capability.",
                        "chunk-1",
                        "docs/sdk",
                        "Text SDK",
                        0.93,
                        Map.of("section", "capabilities"))),
                List.of(Map.of("id", "source-1", "title", "SDK docs")));
        TextGenerationRequest typed = new TextGenerationRequest(
                "model-context",
                List.of(TextGenerationMessage.user("What is AQLI?")),
                TextGenerationParameters.unspecified(),
                List.of(), null, Map.of(), null, null, null, null, null, context);

        InferenceRequest mapped = TextGenerationRequestMapper.toInferenceRequest(typed);

        assertEquals("embedding-model-v2", mapped.getParameters().get("embedding_model"));
        assertEquals(true, mapped.getParameters().get("rag_enabled"));
        assertEquals(1, mapped.getParameters().get("rag_context") instanceof List<?> values ? values.size() : 0);
        assertEquals(1, mapped.getParameters().get("rag_sources") instanceof List<?> values ? values.size() : 0);
        assertEquals(true, mapped.getMetadata().get("rag_context_injected"));
        assertEquals(1, mapped.getMetadata().get("rag_context_items"));
        assertEquals(Message.Role.SYSTEM, mapped.getMessages().get(1).getRole());
        assertTrue(mapped.getMessages().get(1).getContent().contains("AQLI exposes a transport-neutral"));
    }
}

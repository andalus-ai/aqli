package tech.kayys.aqli.sdk.core;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.sdk.capability.text.TextGenerationParameters;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;

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
    void mapsTypedPublicRequestAndToolDefinitionsIntoInternalExecutionRequest() {
        ToolDefinition tool = ToolDefinition.builder().name("lookup").build();
        TextGenerationRequest publicRequest = new TextGenerationRequest(
                "model-typed",
                java.util.List.of(Message.system("be concise"), Message.user("hello")),
                new TextGenerationParameters(
                        0.35, 0.82, 24, 96, 1.12, 0.04, 7,
                        true, "root ::= \"ok\"", java.util.List.of("END")),
                java.util.List.of(tool));

        InferenceRequest mapped = TextGenerationRequestMapper.toInferenceRequest(publicRequest);

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
}

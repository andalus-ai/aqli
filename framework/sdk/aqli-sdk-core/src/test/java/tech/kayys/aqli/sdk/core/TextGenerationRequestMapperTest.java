package tech.kayys.aqli.sdk.core;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.inference.InferenceRequest;

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

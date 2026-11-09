package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import tech.kayys.aljabr.spi.inference.InferenceRequest;

class GgufInferenceRequestFactoryTest {

    @Test
    void createsRequestWithFormattedPromptAndSamplingOptions() {
        InferenceRequest request = GgufInferenceRequestFactory.create(
                Path.of("gemma.gguf"),
                "<user>hello</user>",
                "hello",
                64,
                0.7d,
                0.9d,
                40,
                1.1d);

        assertNotNull(request);
        assertEquals("gemma.gguf", request.getModel());
        assertEquals("<user>hello</user>", request.getPrompt());
        assertEquals(64, request.getMaxTokens());
        assertEquals(0.7d, request.getTemperature());
        assertEquals(0.9d, request.getTopP());
        assertEquals(40, request.getTopK());
        assertEquals(1.1d, request.getRepeatPenalty());
    }

    @Test
    void appliesDeterministicRepeatPenaltyWhenNoneWasSpecified() {
        InferenceRequest request = GgufInferenceRequestFactory.create(
                Path.of("gemma.gguf"), "formatted", "raw", 8, 0.0d, 1.0d, 1, Double.NaN);

        assertEquals(1.0d, request.getRepeatPenalty());
    }

    @Test
    void leavesRepeatPenaltyUnsetForSamplingWhenNotSpecified() {
        InferenceRequest request = GgufInferenceRequestFactory.create(
                Path.of("gemma.gguf"), "formatted", "raw", 8, 0.5d, 1.0d, 1, Double.NaN);

        assertEquals(Double.NaN, request.getRepeatPenalty());
    }

    @Test
    void rejectsMissingRequiredInputs() {
        assertThrows(NullPointerException.class, () -> GgufInferenceRequestFactory.create(
                null, "formatted", "raw", 8, 0.5d, 1.0d, 1, Double.NaN));
        assertThrows(NullPointerException.class, () -> GgufInferenceRequestFactory.create(
                Path.of("gemma.gguf"), null, "raw", 8, 0.5d, 1.0d, 1, Double.NaN));
        assertThrows(NullPointerException.class, () -> GgufInferenceRequestFactory.create(
                Path.of("gemma.gguf"), "formatted", null, 8, 0.5d, 1.0d, 1, Double.NaN));
    }
}

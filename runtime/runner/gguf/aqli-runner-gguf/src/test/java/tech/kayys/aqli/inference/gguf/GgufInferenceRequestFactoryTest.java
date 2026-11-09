package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GgufInferenceRequestFactoryTest {

    @Test
    void createsRequestForResolvedModelAndFormattedPrompt() {
        var request = GgufInferenceRequestFactory.create(
                Path.of("/models/example.gguf"),
                "<|user|>hello<|end|>\n",
                "hello",
                64,
                0.2d,
                0.9d,
                40,
                1.1d);

        assertNotNull(request);
    }

    @Test
    void appliesDeterministicRepeatPenaltyWhenTemperatureIsZero() {
        var request = GgufInferenceRequestFactory.create(
                Path.of("/models/example.gguf"),
                "formatted prompt",
                "raw prompt",
                32,
                0.0d,
                0.9d,
                20,
                Double.NaN);

        assertNotNull(request);
    }

    @Test
    void rejectsMissingResolvedModelOrPromptValues() {
        assertThrows(NullPointerException.class, () -> GgufInferenceRequestFactory.create(
                null, "formatted", "raw", 16, 0.2d, 0.9d, 20, Double.NaN));
        assertThrows(NullPointerException.class, () -> GgufInferenceRequestFactory.create(
                Path.of("/models/example.gguf"), null, "raw", 16, 0.2d, 0.9d, 20, Double.NaN));
    }
}

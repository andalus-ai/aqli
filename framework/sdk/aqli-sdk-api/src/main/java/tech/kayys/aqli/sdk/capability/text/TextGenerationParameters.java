package tech.kayys.aqli.sdk.capability.text;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed, optional controls for text generation.
 *
 * <p>Null numeric/boolean values mean that the caller did not specify the
 * control; defaults are applied once by the SDK execution boundary.</p>
 */
public record TextGenerationParameters(
        Double temperature,
        Double topP,
        Integer topK,
        Integer maxTokens,
        Double repeatPenalty,
        Double minP,
        Integer seed,
        Boolean jsonMode,
        String grammar,
        List<String> stopSequences,
        Double presencePenalty,
        Double frequencyPenalty,
        Map<String, Object> responseFormat) {

    public TextGenerationParameters {
        stopSequences = stopSequences == null ? List.of() : List.copyOf(stopSequences);
        if (stopSequences.stream().anyMatch(value -> value == null || value.isEmpty())) {
            throw new IllegalArgumentException("stopSequences must not contain null or empty values");
        }
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (topP != null && (topP <= 0.0 || topP > 1.0)) {
            throw new IllegalArgumentException("topP must be greater than 0.0 and at most 1.0");
        }
        if (topK != null && topK < 0) {
            throw new IllegalArgumentException("topK must not be negative");
        }
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (repeatPenalty != null && repeatPenalty <= 0.0) {
            throw new IllegalArgumentException("repeatPenalty must be positive");
        }
        if (minP != null && (minP < 0.0 || minP > 1.0)) {
            throw new IllegalArgumentException("minP must be between 0.0 and 1.0");
        }
        validatePenalty("presencePenalty", presencePenalty);
        validatePenalty("frequencyPenalty", frequencyPenalty);
        responseFormat = responseFormat == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(responseFormat));
    }

    /** Preserves the original constructor shape. */
    public TextGenerationParameters(
            Double temperature,
            Double topP,
            Integer topK,
            Integer maxTokens,
            Double repeatPenalty,
            Double minP,
            Integer seed,
            Boolean jsonMode,
            String grammar,
            List<String> stopSequences) {
        this(temperature, topP, topK, maxTokens, repeatPenalty, minP, seed,
                jsonMode, grammar, stopSequences, null, null, Map.of());
    }

    public static TextGenerationParameters unspecified() {
        return new TextGenerationParameters(
                null, null, null, null, null, null, null, null, null, List.of(),
                null, null, Map.of());
    }

    private static void validatePenalty(String name, Double value) {
        if (value != null && (value < -2.0 || value > 2.0)) {
            throw new IllegalArgumentException(name + " must be between -2.0 and 2.0");
        }
    }
}

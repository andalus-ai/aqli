package tech.kayys.aqli.inference.gguf;

import java.nio.file.Path;
import java.util.Objects;

import tech.kayys.aljabr.spi.Message;
import tech.kayys.aljabr.spi.inference.InferenceRequest;

/**
 * Builds the Aljabr inference request used by the Java-native GGUF runner.
 *
 * <p>Prompt formatting remains a runtime concern because it depends on the
 * resolved model and its chat template. This factory only translates the
 * already-resolved prompt and sampling options into the canonical request.</p>
 */
public final class GgufInferenceRequestFactory {

    private GgufInferenceRequestFactory() {
    }

    public static InferenceRequest create(
            Path modelPath,
            String formattedPrompt,
            String userPrompt,
            int maxTokens,
            double temperature,
            double topP,
            int topK,
            double repeatPenalty) {
        Objects.requireNonNull(modelPath, "modelPath");
        Objects.requireNonNull(formattedPrompt, "formattedPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");

        var builder = InferenceRequest.builder()
                .model(modelPath.getFileName().toString())
                .prompt(formattedPrompt)
                .message(Message.user(userPrompt))
                .maxTokens(maxTokens)
                .temperature(temperature)
                .topP(topP)
                .topK(topK);

        if (!Double.isNaN(repeatPenalty)) {
            builder.repeatPenalty(repeatPenalty);
        } else if (temperature <= 0.0d) {
            builder.repeatPenalty(1.0d);
        }
        return builder.build();
    }
}

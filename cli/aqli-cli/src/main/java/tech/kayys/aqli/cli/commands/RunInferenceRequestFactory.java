package tech.kayys.aqli.cli.commands;

import java.util.UUID;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Builds the common execution-request foundation for the {@code aqli run} CLI.
 *
 * <p>This is a CLI adapter, not the public SDK request contract. Capability-specific
 * mappings (vision, audio, image generation, tools, and retrieval) are applied by
 * their respective CLI mappers after this base request is created.</p>
 */
final class RunInferenceRequestFactory {

    private RunInferenceRequestFactory() {
    }

    static InferenceRequest.Builder createBase(Options options) {
        if (options == null) {
            throw new IllegalArgumentException("run request options are required");
        }

        InferenceRequest.Builder request = InferenceRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .model(options.model())
                .prompt(options.prompt())
                .temperature(options.temperature())
                .topP(options.topP())
                .topK(options.topK())
                .repeatPenalty(options.repeatPenalty())
                .jsonMode(options.jsonMode())
                .maxTokens(options.maxTokens())
                .plugin(options.pluginId())
                .streaming(options.streaming());

        if (options.sessionId() != null && !options.sessionId().isBlank()) {
            request.sessionId(options.sessionId().trim());
        }
        if (options.mirostat() > 0) {
            request.mirostat(options.mirostat());
        }
        if (options.grammar() != null && !options.grammar().isEmpty()) {
            request.grammar(options.grammar());
        }
        request.cacheBypass(options.cacheBypass());
        return request;
    }

    record Options(
            String model,
            String prompt,
            double temperature,
            double topP,
            int topK,
            double repeatPenalty,
            boolean jsonMode,
            int maxTokens,
            String pluginId,
            boolean streaming,
            String sessionId,
            int mirostat,
            String grammar,
            boolean cacheBypass) {
    }
}

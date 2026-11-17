package tech.kayys.aqli.sdk.core;

/**
 * Transport-neutral options for text generation.
 *
 * <p>This type is the shared semantic representation of text-generation
 * controls. CLI, REST, and gRPC adapters should translate their own input
 * formats into this value rather than independently mutating the internal
 * inference request.</p>
 */
public record TextGenerationOptions(
        double temperature,
        double topP,
        int topK,
        double repeatPenalty,
        boolean jsonMode,
        int maxTokens,
        int mirostat,
        String grammar) {
}

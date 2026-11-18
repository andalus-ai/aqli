package tech.kayys.aqli.sdk.capability.text;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Canonical SDK operations for text generation.
 *
 * <p>Local runtimes and remote clients implement the same request/response
 * semantics. Protocol-specific adapters should map their wire payloads to
 * these types and delegate here rather than reimplementing generation rules.</p>
 */
public interface TextGenerationOperations {

    default TextGenerationResponse generateText(TextGenerationRequest request) {
        throw new UnsupportedOperationException("Text generation is not supported by this SDK implementation");
    }

    default CompletionStage<TextGenerationResponse> generateTextAsync(TextGenerationRequest request) {
        throw new UnsupportedOperationException("Asynchronous text generation is not supported by this SDK implementation");
    }

    default Flow.Publisher<TextGenerationChunk> streamText(TextGenerationRequest request) {
        throw new UnsupportedOperationException("Text streaming is not supported by this SDK implementation");
    }
}

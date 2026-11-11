package tech.kayys.aqli.sdk.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import tech.kayys.aljabr.spi.model.ModelInfo;

/**
 * Public, transport-neutral inference client contract.
 *
 * <p>Implementations may execute in-process or communicate with a remote AQLI
 * service. Transport selection and runtime composition are implementation
 * details; the request and result contract remains the same.</p>
 */
public interface AqliClient extends AutoCloseable {
    GenerationResult generate(String prompt);
    GenerationResult generate(GenerationRequest request);
    GenerationStream generateStream(String prompt);
    List<GenerationResult> generateBatch(List<String> prompts);
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
    ModelInfo modelInfo();
    boolean supports(Feature feature);

    @Override
    void close();

    /**
     * Resolve the optional implementation module's builder through the SDK
     * provider SPI. The API artifact itself has no transport/runtime dependency.
     */
    static Builder builder() {
        return java.util.ServiceLoader.load(AqliClientProvider.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No AqliClientProvider is available. Add an AQLI SDK implementation, "
                        + "such as the local or remote SDK adapter."))
                .createBuilder();
    }

    enum Feature {
        KV_CACHE,
        SPECULATIVE_DECODING,
        MULTIMODAL,
        FLASH_ATTENTION,
        QUANTIZATION_INT8,
        QUANTIZATION_FP16,
        STREAMING,
        BATCH_INFERENCE
    }

    record GenerationRequest(
            String prompt,
            int maxTokens,
            float temperature,
            float topP,
            List<String> stopTokens) {
        public GenerationRequest {
            java.util.Objects.requireNonNull(prompt, "prompt");
            stopTokens = stopTokens == null ? List.of() : List.copyOf(stopTokens);
        }

        public static GenerationRequest of(String prompt) {
            return new GenerationRequest(prompt, 512, 0.7f, 0.9f, List.of());
        }
    }

    record GenerationResult(String text, int tokenCount, int promptTokens, long durationMs) {
        public float tokensPerSecond() {
            return durationMs > 0 ? tokenCount * 1000f / durationMs : 0f;
        }
    }

    interface GenerationStream {
        GenerationStream onToken(Consumer<String> handler);
        GenerationStream onComplete(Consumer<GenerationResult> handler);
        GenerationStream onError(Consumer<Throwable> handler);
        CompletableFuture<GenerationResult> toFuture();
    }

    interface Builder {
        Builder model(String model);
        Builder endpoint(String endpoint);
        Builder maxTokens(int maxTokens);
        Builder temperature(float temperature);
        Builder backend(String backend);
        /** Selects the client connection transport. */
        Builder transport(ClientTransport transport);

        /**
         * @deprecated Use {@link #transport(ClientTransport)}.
         */
        @Deprecated(since = "0.2", forRemoval = false)
        Builder mode(DeploymentMode mode);
        AqliClient build();
    }
}

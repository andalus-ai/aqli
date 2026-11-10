package tech.kayys.aqli.sdk.core;

import io.smallrye.mutiny.Multi;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.spi.batch.BatchInferenceRequest;
import tech.kayys.aqli.spi.embedding.EmbeddingRequest;
import tech.kayys.aqli.spi.embedding.EmbeddingResponse;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aljabr.spi.model.MultimodalRequest;
import tech.kayys.aljabr.spi.model.MultimodalResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Capability port for model inference operations exposed by the AQLI SDK.
 *
 * <p>This contract is independent of model format and execution backend.
 * Implementations may provide synchronous, asynchronous, streaming,
 * embedding, batch, and multimodal operations according to their capabilities.</p>
 */
public interface InferenceOperations {

    InferenceResponse createCompletion(InferenceRequest request) throws SdkException;

    CompletableFuture<InferenceResponse> createCompletionAsync(InferenceRequest request);

    Multi<StreamingInferenceChunk> streamCompletion(InferenceRequest request);

    EmbeddingResponse createEmbedding(EmbeddingRequest request) throws SdkException;

    List<InferenceResponse> batchInference(BatchInferenceRequest batchRequest) throws SdkException;

    default InferenceResponse inferGguf(String modelId, InferenceRequest request) throws SdkException {
        return createCompletion(request.toBuilder().model(modelId).preferredProvider("gguf").build());
    }

    default InferenceResponse inferSafeTensors(String modelId, InferenceRequest request) throws SdkException {
        return createCompletion(request.toBuilder().model(modelId).preferredProvider("safetensor").build());
    }

    default Multi<StreamingInferenceChunk> streamGguf(String modelId, InferenceRequest request) {
        return streamCompletion(request.toBuilder().model(modelId).preferredProvider("gguf").streaming(true).build());
    }

    default Multi<StreamingInferenceChunk> streamSafeTensors(String modelId, InferenceRequest request) {
        return streamCompletion(request.toBuilder().model(modelId).preferredProvider("safetensor").streaming(true).build());
    }

    default EmbeddingResponse embed(String modelId, String text) throws SdkException {
        return createEmbedding(EmbeddingRequest.builder().model(modelId).input(text).build());
    }

    default MultimodalResponse processMultimodal(MultimodalRequest request) throws SdkException {
        throw new UnsupportedOperationException("Multimodal processing is not supported by this SDK implementation.");
    }
}

package tech.kayys.aqli.safetensor.engine.sdk;

import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aqli.sdk.model.SystemInfo;
import tech.kayys.aqli.spi.batch.BatchInferenceRequest;
import tech.kayys.aqli.spi.embedding.EmbeddingRequest;
import tech.kayys.aqli.spi.embedding.EmbeddingResponse;
import tech.kayys.aqli.spi.inference.AsyncJobStatus;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aljabr.spi.model.ModelInfo;
import io.smallrye.mutiny.Multi;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SafetensorAqliSdk implements AqliSdk {
    @Override public InferenceResponse createCompletion(InferenceRequest request) throws SdkException { return null; }
    @Override public CompletableFuture<InferenceResponse> createCompletionAsync(InferenceRequest request) { return null; }
    @Override public Multi<StreamingInferenceChunk> streamCompletion(InferenceRequest request) { return null; }
    @Override public EmbeddingResponse createEmbedding(EmbeddingRequest request) throws SdkException { return null; }
    @Override public String submitAsyncJob(InferenceRequest request) throws SdkException { return null; }
    @Override public AsyncJobStatus getJobStatus(String jobId) throws SdkException { return null; }
    @Override public AsyncJobStatus waitForJob(String jobId, Duration maxWaitTime, Duration pollInterval) throws SdkException { return null; }
    @Override public List<InferenceResponse> batchInference(BatchInferenceRequest batchRequest) throws SdkException { return List.of(); }
    @Override public List<ModelInfo> listModels() throws SdkException { return List.of(); }
    @Override public List<ModelInfo> listModels(int offset, int limit) throws SdkException { return List.of(); }
    @Override public Optional<ModelInfo> getModelInfo(String modelId) throws SdkException { return Optional.empty(); }
    @Override public void pullModel(String modelSpec, Consumer<PullProgress> progressCallback) throws SdkException {}
    @Override public void deleteModel(String modelId) throws SdkException {}
    @Override public SystemInfo getSystemInfo() throws SdkException { return null; }
}

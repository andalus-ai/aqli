package tech.kayys.aqli.sdk.local;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.local.engine.LocalInferenceEngineCatalog;
import tech.kayys.aljabr.spi.model.ModelInfo;
import tech.kayys.aqli.sdk.model.ModelListRequest;
import tech.kayys.aqli.sdk.model.ModelPullRequest;
import tech.kayys.aqli.sdk.model.ModelResolution;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aqli.sdk.model.SystemInfo;
import tech.kayys.aqli.spi.batch.BatchInferenceRequest;
import tech.kayys.aqli.spi.embedding.EmbeddingRequest;
import tech.kayys.aqli.spi.embedding.EmbeddingResponse;
import tech.kayys.aqli.spi.inference.AsyncJobStatus;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aljabr.spi.model.ModelManifest;
import tech.kayys.aljabr.spi.model.ModelRegistry;

/**
 * Local SDK facade over the AQLI inference-engine catalog.
 *
 * <p>This implementation deliberately exposes only capabilities backed by
 * real services. Model management, embeddings, batch inference and job
 * operations remain unsupported until their corresponding runtime services
 * are composed here.</p>
 */
public final class LocalAqliSdk implements AqliSdk {

    private final LocalInferenceEngineCatalog engines;
    private final SdkConfig config;

    public LocalAqliSdk(LocalInferenceEngineCatalog engines, SdkConfig config) {
        this.engines = java.util.Objects.requireNonNull(engines, "engines");
        this.config = config != null ? config : SdkConfig.builder().build();
    }

    @Override
    public InferenceResponse createCompletion(InferenceRequest request) throws SdkException {
        InferenceRequest effective = applyDefaults(request);
        try {
            var selection = engines.select(effective);
            var timeout = effective.getTimeout().orElse(config.getRequestTimeout());
            return selection.engine().infer(effective).await().atMost(timeout);
        } catch (RuntimeException e) {
            throw new SdkException("SDK_ERR_INFERENCE",
                    "Local inference failed for model '" + request.getModel() + "': " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<InferenceResponse> createCompletionAsync(InferenceRequest request) {
        try {
            InferenceRequest effective = applyDefaults(request);
            var selection = engines.select(effective);
            return selection.engine().infer(effective).subscribeAsCompletionStage().toCompletableFuture();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public Multi<StreamingInferenceChunk> streamCompletion(InferenceRequest request) {
        try {
            InferenceRequest effective = applyDefaults(request).toBuilder().streaming(true).build();
            return engines.select(effective).engine().stream(effective);
        } catch (RuntimeException e) {
            return Multi.createFrom().failure(e);
        }
    }

    @Override
    public EmbeddingResponse createEmbedding(EmbeddingRequest request) throws SdkException {
        throw unsupported("Embeddings");
    }

    @Override
    public String submitAsyncJob(InferenceRequest request) throws SdkException {
        throw unsupported("Inference jobs");
    }

    @Override
    public AsyncJobStatus getJobStatus(String jobId) throws SdkException {
        throw unsupported("Inference job status");
    }

    @Override
    public AsyncJobStatus waitForJob(String jobId, java.time.Duration maxWaitTime,
            java.time.Duration pollInterval) throws SdkException {
        throw unsupported("Inference job waiting");
    }

    @Override
    public List<InferenceResponse> batchInference(BatchInferenceRequest batchRequest) throws SdkException {
        throw unsupported("Batch inference");
    }

    @Override
    public List<ModelInfo> listModels() throws SdkException {
        throw unsupported("Model catalogue");
    }

    @Override
    public List<ModelInfo> listModels(int offset, int limit) throws SdkException {
        throw unsupported("Model catalogue");
    }

    @Override
    public Optional<ModelInfo> getModelInfo(String modelId) throws SdkException {
        throw unsupported("Model lookup");
    }

    @Override
    public void pullModel(String modelSpec, Consumer<PullProgress> progressCallback) throws SdkException {
        throw unsupported("Model downloads");
    }

    @Override
    public void deleteModel(String modelId) throws SdkException {
        throw unsupported("Model deletion");
    }

    @Override
    public SystemInfo getSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        String version = Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("development");
        return SystemInfo.builder()
                .cliVersion(version)
                .javaVersion(System.getProperty("java.version"))
                .osName(System.getProperty("os.name"))
                .osVersion(System.getProperty("os.version"))
                .osArch(System.getProperty("os.arch"))
                .userName(System.getProperty("user.name"))
                .userHome(System.getProperty("user.home"))
                .totalMemory(runtime.totalMemory())
                .freeMemory(runtime.freeMemory())
                .maxMemory(runtime.maxMemory())
                .availableProcessors(runtime.availableProcessors())
                .metadata(Map.of(
                        "heapUsedBytes", heap.getUsed(),
                        "heapCommittedBytes", heap.getCommitted(),
                        "availableProviders", engines.availableProviders()))
                .build();
    }

    @Override
    public Optional<ModelRegistry.ModelStats> getModelStats(String modelId) throws SdkException {
        throw unsupported("Per-model statistics");
    }

    @Override
    public ModelManifest registerModel(ModelRegistry.ModelUploadRequest request) throws SdkException {
        throw unsupported("Model registration");
    }

    @Override
    public ModelResolution importModel(java.nio.file.Path source, boolean move) throws SdkException {
        throw unsupported("Model import");
    }

    @Override
    public List<String> getRecentLogs(int maxLines) {
        return List.of();
    }

    @Override
    public Map<String, Object> getMetrics(String providerId, String modelId) {
        return Map.of("availableProviders", engines.availableProviders());
    }

    private InferenceRequest applyDefaults(InferenceRequest request) {
        java.util.Objects.requireNonNull(request, "request");
        if (request.getPreferredProvider().isPresent() || config.getPreferredProvider().isEmpty()) {
            return request;
        }
        return request.toBuilder().preferredProvider(config.getPreferredProvider().orElseThrow()).build();
    }

    private static SdkException unsupported(String capability) {
        return new SdkException("SDK_ERR_NOT_SUPPORTED",
                capability + " is not available in the local SDK: no production runtime service is composed.");
    }
}

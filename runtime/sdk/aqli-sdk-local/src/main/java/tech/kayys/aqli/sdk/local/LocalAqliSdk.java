package tech.kayys.aqli.sdk.local;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.smallrye.mutiny.Multi;
import tech.kayys.aljabr.spi.download.DownloadProgressListener;
import tech.kayys.aqli.model.repo.hf.HuggingFaceRepository;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.local.engine.LocalInferenceEngineCatalog;
import tech.kayys.aljabr.spi.model.ModelInfo;
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
public class LocalAqliSdk implements AqliSdk {

    private final LocalInferenceEngineCatalog engines;
    private final SdkConfig config;
    private final LocalModelCatalog models;
    private final HuggingFaceRepository huggingFaceRepository;

    public LocalAqliSdk(LocalInferenceEngineCatalog engines, SdkConfig config) {
        this(engines, config, new LocalModelCatalog(new tech.kayys.aqli.model.repo.local.ManifestStore()), null);
    }

    public LocalAqliSdk(LocalInferenceEngineCatalog engines, SdkConfig config, LocalModelCatalog models) {
        this(engines, config, models, null);
    }

    public LocalAqliSdk(LocalInferenceEngineCatalog engines, SdkConfig config, LocalModelCatalog models,
            HuggingFaceRepository huggingFaceRepository) {
        this.engines = java.util.Objects.requireNonNull(engines, "engines");
        this.config = config != null ? config : SdkConfig.builder().build();
        this.models = java.util.Objects.requireNonNull(models, "models");
        this.huggingFaceRepository = huggingFaceRepository;
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
        return models.listModels(0, Integer.MAX_VALUE);
    }

    @Override
    public List<ModelInfo> listModels(int offset, int limit) throws SdkException {
        return models.listModels(offset, limit);
    }

    @Override
    public Optional<ModelInfo> getModelInfo(String modelId) throws SdkException {
        return models.findModel(modelId);
    }

    @Override
    public void pullModel(String modelSpec, Consumer<PullProgress> progressCallback) throws SdkException {
        pullModel(modelSpec, null, null, false, progressCallback);
    }

    @Override
    public void pullModel(String modelSpec, String revision, String format, boolean force,
            Consumer<PullProgress> progressCallback) throws SdkException {
        if (huggingFaceRepository == null) {
            throw unsupported("Hugging Face model downloads");
        }
        String repositoryId = resolveHuggingFaceRepositoryId(modelSpec);
        DownloadProgressListener listener = new DownloadProgressListener() {
            @Override
            public void onStart(long totalBytes) {
                if (progressCallback != null) {
                    progressCallback.accept(PullProgress.of("Downloading " + repositoryId, null, totalBytes, 0));
                }
            }

            @Override
            public void onProgress(long downloadedBytes, long totalBytes, double progress) {
                if (progressCallback != null) {
                    progressCallback.accept(PullProgress.of(
                            "Downloading " + repositoryId, null, totalBytes, downloadedBytes));
                }
            }

            @Override
            public void onComplete(long totalBytes) {
                if (progressCallback != null) {
                    progressCallback.accept(PullProgress.of(
                            "Completed " + repositoryId, null, totalBytes, totalBytes));
                }
            }

            @Override
            public void onError(Throwable error) {
                if (progressCallback != null) {
                    progressCallback.accept(PullProgress.of(
                            "Failed " + repositoryId + ": "
                                    + (error == null ? "unknown error" : error.getMessage())));
                }
            }
        };

        try {
            huggingFaceRepository.pullRepository(
                    repositoryId, revision, format, null, Map.of(), List.of(), force, null, listener);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SdkException("SDK_ERR_MODEL_PULL",
                    "Hugging Face model pull was interrupted for '" + repositoryId + "'.", e);
        } catch (IOException | RuntimeException e) {
            throw new SdkException("SDK_ERR_MODEL_PULL",
                    "Unable to pull Hugging Face model '" + repositoryId + "': " + e.getMessage(), e);
        }
    }

    private static String resolveHuggingFaceRepositoryId(String modelSpec) throws SdkException {
        if (modelSpec == null || modelSpec.isBlank()) {
            throw new SdkException("SDK_ERR_INVALID_ARGUMENT", "Model specification is required.");
        }
        String normalized = modelSpec.trim();
        if (normalized.startsWith("hf:")) {
            normalized = normalized.substring("hf:".length());
        } else if (normalized.startsWith("huggingface:")) {
            normalized = normalized.substring("huggingface:".length());
        } else {
            if (normalized.contains("://") || normalized.startsWith("/") || normalized.indexOf('\\') >= 0) {
                throw new SdkException("SDK_ERR_NOT_SUPPORTED",
                        "The local SDK model pull currently supports Hugging Face repository references only.");
            }
            try {
                if (java.nio.file.Files.exists(Path.of(normalized))) {
                    throw new SdkException("SDK_ERR_NOT_SUPPORTED",
                            "Local file imports are not supported by the Hugging Face pull operation.");
                }
            } catch (InvalidPathException e) {
                throw new SdkException("SDK_ERR_INVALID_ARGUMENT", "Invalid model specification: " + modelSpec, e);
            }
        }
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.endsWith("/")
                || normalized.chars().filter(ch -> ch == '/').count() != 1
                || normalized.contains(":") || normalized.indexOf('\\') >= 0) {
            throw new SdkException("SDK_ERR_INVALID_ARGUMENT",
                    "Expected a Hugging Face repository ID in the form 'owner/repository'.");
        }
        return normalized;
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
    public List<String> listAvailableProviders() {
        return engines.availableProviders();
    }

    @Override
    public List<String> getRecentLogs(int maxLines) throws SdkException {
        throw unsupported("Runtime log retrieval");
    }

    @Override
    public Map<String, Object> getMetrics(String providerId, String modelId) throws SdkException {
        throw unsupported("Inference metrics");
    }

    @Override
    public List<tech.kayys.aqli.spi.plugin.AqliPlugin.PluginMetadata> listPlugins() throws SdkException {
        throw unsupported("Plugin catalogue");
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

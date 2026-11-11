package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.model.ModelResolution;
import tech.kayys.aqli.sdk.model.ModelPullRequest;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aljabr.spi.model.ModelManifest;
import tech.kayys.aljabr.spi.model.ModelRegistry;

import java.util.function.Consumer;

/**
 * Model preparation and lifecycle operations.
 *
 * <p>This port owns resolving/preparing models, downloading, importing,
 * registering, converting, and deleting model artifacts. Catalogue reads
 * are inherited from {@link ModelCatalogOperations}.</p>
 */
public interface ModelLifecycleOperations extends ModelCatalogOperations {

    /**
     * Resolve and pull a model if necessary. NEW in v1.2.1.
     */
    default ModelResolution prepareModel(String modelId, boolean forceGguf, Consumer<PullProgress> progressCallback)
            throws SdkException {
        return prepareModel(modelId, forceGguf, "Q4_K_M", progressCallback);
    }

    /**
     * Resolve and pull a model if necessary with quantization control.
     * 
     * @param modelId          Model identifier
     * @param forceGguf        Force GGUF format
     * @param quantization     Quantization type (Q4_0, Q4_K_M, Q5_0, Q5_K_M, Q6_K,
     *                         Q8_0, F16, F32)
     * @param progressCallback Progress callback
     * @return Model resolution
     */
    default ModelResolution prepareModel(String modelId, boolean forceGguf, String quantization,
            Consumer<PullProgress> progressCallback) throws SdkException {
        return prepareModel(modelId, null, null, forceGguf, quantization, java.util.List.of(), progressCallback);
    }

    /**
     * Resolve and pull a model if necessary with explicit format and quantization.
     * NEW in v1.2.4.
     */
    default ModelResolution prepareModel(String modelId, String format, String plugin, boolean forceGguf,
            String quantization, Consumer<PullProgress> progressCallback) throws SdkException {
        return prepareModel(modelId, format, plugin, forceGguf, quantization, java.util.List.of(), progressCallback);
    }

    /**
     * Resolve and pull a model if necessary with explicit fallback candidates.
     */
    default ModelResolution prepareModel(String modelId, String format, String plugin, boolean forceGguf,
            String quantization, java.util.List<String> fallbackModelIds, Consumer<PullProgress> progressCallback)
            throws SdkException {
        return new ModelResolution(modelId, null, getModelInfo(modelId).orElse(null));
    }

    /**
     * Explicitly convert a model (checkpoint/tensor) to GGUF format.
     * NEW in v1.2.3.
     */
    default ModelResolution convertToGguf(ModelResolution source, String quantization,
            Consumer<PullProgress> progressCallback) throws SdkException {
        throw new UnsupportedOperationException("GGUF conversion not supported by this SDK implementation.");
    }

    void pullModel(String modelSpec, Consumer<PullProgress> progressCallback) throws SdkException;

    /**
     * Pull a model with enhanced options. NEW in v1.3.0.
     */
    default void pullModel(ModelPullRequest request, Consumer<PullProgress> progressCallback) throws SdkException {
        pullModel(request.modelSpec(), request.revision(), request.format(), request.force(), progressCallback);
    }

    /**
     * Pull a model with explicit revision and force options. NEW in v1.2.2.
     * 
     * @param modelSpec        model name or hf:RepoId
     * @param revision         branch, tag or commit hash (null for default)
     * @param force            force re-download even if files exist
     * @param progressCallback progress updates
     */
    default void pullModel(String modelSpec, String revision, boolean force, Consumer<PullProgress> progressCallback)
            throws SdkException {
        pullModel(modelSpec, revision, null, force, progressCallback);
    }

    /**
     * Pull a model with explicit format, revision and force options. NEW in v1.2.4.
     */
    default void pullModel(String modelSpec, String revision, String format, boolean force,
            Consumer<PullProgress> progressCallback) throws SdkException {
        // Default implementation for backward compatibility or implementations that
        // don't support revision/force yet.
        if (revision == null && format == null && !force) {
            pullModel(modelSpec, progressCallback);
        } else {
            throw new SdkException("SDK_ERR_NOT_SUPPORTED",
                    "This SDK implementation does not support explicit revision, format or force pull.");
        }
    }

    /**
     * Import a local model file or directory into the SDK's managed repository.
     * NEW in v1.2.5.
     */
    default ModelResolution importModel(java.nio.file.Path source, boolean move) throws SdkException {
        throw new UnsupportedOperationException("Importing models not supported by this SDK implementation");
    }

    void deleteModel(String modelId) throws SdkException;

    /**
     * Pull a GGUF model by name or HuggingFace repo. NEW in v0.1.4.
     *
     * @param modelSpec e.g. {@code "hf:TheBloke/Llama-2-7B-GGUF"} or
     *                  {@code "tinyllama"} (resolved via Ollama registry)
     * @param callback  progress callback (nullable)
     */
    default void pullGgufModel(String modelSpec, Consumer<PullProgress> callback) throws SdkException {
        pullModel(modelSpec.startsWith("gguf:") ? modelSpec : "gguf:" + modelSpec, callback);
    }

    /**
     * Pull a SafeTensors checkpoint from HuggingFace. NEW in v0.1.4.
     *
     * @param repoId   HuggingFace repo id, e.g. {@code "bert-base-uncased"}
     * @param callback progress callback (nullable)
     */
    default void pullSafeTensorsModel(String repoId, Consumer<PullProgress> callback) throws SdkException {
        pullModel("hf:" + repoId, callback);
    }

    /**
     * Register a new model programmatically.
     */
    default ModelManifest registerModel(ModelRegistry.ModelUploadRequest request) throws SdkException {
        throw new UnsupportedOperationException("Model registration not supported by this SDK implementation");
    }

}

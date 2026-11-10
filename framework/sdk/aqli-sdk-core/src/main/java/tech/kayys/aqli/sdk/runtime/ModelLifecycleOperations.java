package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.model.ModelResolution;
import tech.kayys.aqli.sdk.model.ModelPullRequest;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aljabr.spi.model.ModelManifest;
import tech.kayys.aljabr.spi.model.ModelRegistry;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Model preparation and lifecycle operations.
 *
 * <p>This port owns resolving/preparing models, downloading, importing,
 * registering, converting, and deleting model artifacts. Catalogue reads
 * are inherited from {@link ModelCatalogOperations}.</p>
 */
public interface ModelLifecycleOperations extends ModelCatalogOperations {

    default ModelResolution prepareModel(String modelId, boolean forceGguf, Consumer<PullProgress> progressCallback)
            throws SdkException {
        return prepareModel(modelId, forceGguf, "Q4_K_M", progressCallback);
    }

    default ModelResolution prepareModel(String modelId, boolean forceGguf, String quantization,
            Consumer<PullProgress> progressCallback) throws SdkException {
        return prepareModel(modelId, null, null, forceGguf, quantization, java.util.List.of(), progressCallback);
    }

    default ModelResolution prepareModel(String modelId, String format, String plugin, boolean forceGguf,
            String quantization, Consumer<PullProgress> progressCallback) throws SdkException {
        return prepareModel(modelId, format, plugin, forceGguf, quantization, java.util.List.of(), progressCallback);
    }

    default ModelResolution prepareModel(String modelId, String format, String plugin, boolean forceGguf,
            String quantization, java.util.List<String> fallbackModelIds, Consumer<PullProgress> progressCallback)
            throws SdkException {
        return new ModelResolution(modelId, null, getModelInfo(modelId).orElse(null));
    }

    default ModelResolution convertToGguf(ModelResolution source, String quantization,
            Consumer<PullProgress> progressCallback) throws SdkException {
        throw new UnsupportedOperationException("GGUF conversion not supported by this SDK implementation.");
    }

    default Optional<tech.kayys.aqli.sdk.util.QuantSuggestionDetector.QuantSuggestion> suggestQuantization(
            String modelId) throws SdkException {
        return getModelInfo(modelId).flatMap(info -> tech.kayys.aqli.sdk.util.QuantSuggestionDetector
                .detect(info.getModelId(), info.getSizeBytes()));
    }

    void pullModel(String modelSpec, Consumer<PullProgress> progressCallback) throws SdkException;

    default void pullModel(ModelPullRequest request, Consumer<PullProgress> progressCallback) throws SdkException {
        pullModel(request.modelSpec(), request.revision(), request.format(), request.force(), progressCallback);
    }

    default void pullModel(String modelSpec, String revision, boolean force, Consumer<PullProgress> progressCallback)
            throws SdkException {
        pullModel(modelSpec, revision, null, force, progressCallback);
    }

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

    default ModelResolution importModel(java.nio.file.Path source, boolean move) throws SdkException {
        throw new UnsupportedOperationException("Importing models not supported by this SDK implementation");
    }

    void deleteModel(String modelId) throws SdkException;

    default void pullGgufModel(String modelSpec, Consumer<PullProgress> callback) throws SdkException {
        pullModel(modelSpec.startsWith("gguf:") ? modelSpec : "gguf:" + modelSpec, callback);
    }

    default void pullSafeTensorsModel(String repoId, Consumer<PullProgress> callback) throws SdkException {
        pullModel("hf:" + repoId, callback);
    }

    default ModelManifest registerModel(ModelRegistry.ModelUploadRequest request) throws SdkException {
        throw new UnsupportedOperationException("Model registration not supported by this SDK implementation");
    }

}

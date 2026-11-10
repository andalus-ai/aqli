package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aljabr.spi.model.ModelInfo;
import tech.kayys.aljabr.spi.model.ModelRegistry;
import tech.kayys.aqli.sdk.model.ModelListRequest;

import java.util.List;
import java.util.Optional;

/**
 * Read-oriented model catalogue operations.
 *
 * <p>This port owns model discovery, filtering, lookup, and catalogue metadata.
 * It deliberately excludes downloads, imports, conversion, and deletion.</p>
 */
public interface ModelCatalogOperations {



    /**
     * List models with filtering and pagination. NEW in v1.3.0.
     */
    default List<ModelInfo> listModels(ModelListRequest request) throws SdkException {
        return listModels(request.offset(), request.limit());
    }

    List<ModelInfo> listModels() throws SdkException;

    List<ModelInfo> listModels(int offset, int limit) throws SdkException;

    /**
     * Legacy method required by old agent-core.
     */
    default List<String> listAvailableProviders() {
        return List.of();
    }

    /**
     * List models filtered by format — NEW in v0.1.4.
     *
     * @param format GGUF, SAFETENSORS, etc. {@code null} = all formats.
     */
    default List<ModelInfo> listModelsByFormat(String format) throws SdkException {
        return listModels().stream()
                .filter(m -> format == null || format.equalsIgnoreCase(m.getFormat()))
                .toList();
    }

    Optional<ModelInfo> getModelInfo(String modelId) throws SdkException;

    /**
     * Resolve a default model if none specified. NEW in v1.2.1.
     */
    default Optional<String> resolveDefaultModel() throws SdkException {
        return listModels(0, 1).stream().findFirst().map(ModelInfo::getModelId);
    }

    /**
     * Get detailed statistics for a model (usage, versions, etc.)
     */
    default Optional<ModelRegistry.ModelStats> getModelStats(String modelId) throws SdkException {
        return Optional.empty();
    }

}

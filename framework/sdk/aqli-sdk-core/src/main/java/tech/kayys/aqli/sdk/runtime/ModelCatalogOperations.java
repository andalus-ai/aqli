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

    default List<ModelInfo> listModels(ModelListRequest request) throws SdkException {
        return listModels(request.offset(), request.limit());
    }

    List<ModelInfo> listModels() throws SdkException;

    List<ModelInfo> listModels(int offset, int limit) throws SdkException;

    default List<String> listAvailableProviders() {
        return List.of();
    }

    default List<ModelInfo> listModelsByFormat(String format) throws SdkException {
        return listModels().stream()
                .filter(m -> format == null || format.equalsIgnoreCase(m.getFormat()))
                .toList();
    }

    Optional<ModelInfo> getModelInfo(String modelId) throws SdkException;

    default Optional<String> resolveDefaultModel() throws SdkException {
        return listModels(0, 1).stream().findFirst().map(ModelInfo::getModelId);
    }

    default Optional<ModelRegistry.ModelStats> getModelStats(String modelId) throws SdkException {
        return Optional.empty();
    }

}

package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.spi.plugin.AqliPlugin;

import java.util.List;

/**
 * Read-oriented plugin discovery exposed by an AQLI runtime.
 */
public interface PluginOperations {

    /**
     * List installed plugins and their metadata.
     *
     * @return installed plugin metadata, or an empty list when unsupported
     */
    default List<AqliPlugin.PluginMetadata> listPlugins() throws SdkException {
        return List.of();
    }
}

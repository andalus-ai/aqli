package tech.kayys.aqli.spi.plugin;

import java.util.List;
import java.util.Optional;

/**
 * Registry for managing plugin lifecycle and discovery.
 */
public interface PluginRegistry {
    void initialize();

    void registerPlugin(AqliPlugin plugin);

    void unregisterPlugin(String pluginId);

    List<AqliPlugin> all();

    <T extends AqliPlugin> List<T> byType(Class<T> type);

    Optional<AqliPlugin> byId(String pluginId);

    void reload(String pluginId);

    List<AqliPlugin.PluginMetadata> listMetadata();

    boolean isHealthy();

    List<String> unhealthyPlugins();

    void shutdownAll();
}

package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.feature.AqliFeatureKit;
import tech.kayys.aqli.sdk.mcp.McpRegistryManager;
import tech.kayys.aqli.spi.plugin.AqliPlugin;

import java.util.List;

/**
 * Aggregate interface for AQLI runtime capabilities.
 *
 * <p>New consumers should depend on focused capability ports where possible.
 * This aggregate remains the compatibility entry point and composes inference,
 * model catalogue, model lifecycle, asynchronous jobs, diagnostics, and
 * runtime-management operations.</p>
 *
 * <h3>Convenience operations</h3>
 * <ul>
 * <li>{@link #listModelsByFormat} — filter the model catalogue by format.</li>
 * <li>{@link #inferGguf} / {@link #inferSafeTensors} — format-aware inference.</li>
 * <li>{@link #streamGguf} / {@link #streamSafeTensors} — format-aware streaming.</li>
 * <li>{@link #embed} — embedding shortcut with a plain string input.</li>
 * <li>{@link #pullGgufModel} / {@link #pullSafeTensorsModel} — format-aware model pulls.</li>
 * </ul>
 */
public interface AqliRuntime extends tech.kayys.aqli.sdk.core.InferenceOperations,
        ModelLifecycleOperations, AsyncJobOperations, RuntimeDiagnosticsOperations {

    // ==================== MCP Operations ====================

    default McpRegistryManager mcpRegistry() {
        throw new UnsupportedOperationException(
                "MCP registry is not supported by this SDK implementation");
    }

    // ==================== Plugin Operations ====================

    /**
     * List all installed plugins and their metadata.
     */
    default List<AqliPlugin.PluginMetadata> listPlugins() throws SdkException {
        return List.of();
    }

    /**
     * ML Kit-style feature entrypoint for face recognition, natural language, and
     * document scanning.
     */
    default AqliFeatureKit featureKit() throws SdkException {
        throw new UnsupportedOperationException(
                "Feature kit is not supported by this SDK implementation.");
    }
}

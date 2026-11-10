package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.feature.AqliFeatureKit;
import tech.kayys.aqli.sdk.mcp.McpRegistryManager;
import tech.kayys.aqli.sdk.model.SystemInfo;
import tech.kayys.aqli.spi.inference.AsyncJobStatus;
import tech.kayys.aqli.spi.plugin.AqliPlugin;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import java.util.List;
import java.util.Map;

/**
 * Aggregate interface for AQLI runtime capabilities.\n *\n * <p>New consumers should depend on focused capability ports such as\n * {@link InferenceOperations} where possible. This aggregate remains the\n * compatibility entry point while the remaining runtime responsibilities are\n * separated into dedicated ports.</p>
 *
 * <h3>New in v0.1.4</h3>
 * <ul>
 * <li>{@link #listModelsByFormat} — filter the model catalogue by format.</li>
 * <li>{@link #inferGguf} / {@link #inferSafeTensors} — convenience methods
 * that pre-set the format hint, skipping runtime detection.</li>
 * <li>{@link #streamGguf} / {@link #streamSafeTensors} — streaming
 * variants.</li>
 * <li>{@link #embed} — embedding shortcut with a plain string input.</li>
 * <li>{@link #pullGgufModel} / {@link #pullSafeTensorsModel} — format-aware
 * model pull helpers.</li>
 * <li>{@link #getSystemInfo()} promoted to non-default.</li>
 * </ul>
 */
public interface AqliRuntime extends tech.kayys.aqli.sdk.core.InferenceOperations, ModelCatalogOperations, ModelLifecycleOperations {

    // ==================== Async Jobs ====================

    String submitAsyncJob(InferenceRequest request) throws SdkException;

    AsyncJobStatus getJobStatus(String jobId) throws SdkException;

    AsyncJobStatus waitForJob(String jobId, java.time.Duration maxWaitTime, java.time.Duration pollInterval)
            throws SdkException;


    // ==================== System Operations ====================

    /**
     * Get information about the underlying hardware and platform.
     */
    default tech.kayys.aqli.plugin.kernel.KernelPlatform getPlatformInfo() throws SdkException {
        return tech.kayys.aqli.plugin.kernel.KernelPlatformDetector.detect();
    }

    // Quantization is an optional integration and is deliberately not exposed
    // through this core SDK contract. Consumers that enable the optional
    // quantization module can use its QuantizationService directly.

    // ==================== MCP Operations ====================

    default McpRegistryManager mcpRegistry() {
        throw new UnsupportedOperationException(
                "MCP registry is not supported by this SDK implementation");
    }

    // ==================== System Operations ====================

    SystemInfo getSystemInfo() throws SdkException;

    /**
     * Retrieve recent logs. NEW in v1.2.1.
     */
    default List<String> getRecentLogs(int maxLines) throws SdkException {
        return List.of();
    }

    // ==================== Advanced Operations (v1.2.2) ====================

    /**
     * List all installed plugins and their metadata.
     */
    default List<AqliPlugin.PluginMetadata> listPlugins() throws SdkException {
        return List.of();
    }

    /**
     * Get real-time performance metrics for a provider/model pair.
     *
     * <p>The returned map includes latency/error-rate data merged with current
     * resource utilization ({@code cpu_load}, {@code heap_used_mb}, etc.).
     *
     * @return Map containing latency, error rate, and resource metrics.
     */
    default Map<String, Object> getMetrics(String providerId, String modelId) throws SdkException {
        return Map.of();
    }

    /**
     * Returns a point-in-time snapshot of CPU and memory utilization.
     * Useful for capacity planning, auto-scaling decisions, and dashboards.
     *
     * <p>CPU values are in the range [0.0, 1.0].
     * Memory values are in bytes.
     *
     * <p>NEW in v1.4.0.
     *
     * @return resource snapshot, or {@link SdkMetricsCollector.ResourceSnapshot#unavailable()}
     *         if not supported by the SDK implementation.
     */
    default SdkMetricsCollector.ResourceSnapshot getResourceSnapshot() throws SdkException {
        return SdkMetricsCollector.ResourceSnapshot.unavailable();
    }

    /**
     * Get detailed statistics for a model (usage, versions, etc.)
     */


    /**
     * Register a new model programmatically.
     */


    /**
     * ML Kit-style feature entrypoint for face recognition, natural language, and
     * document scanning.
     */
    default AqliFeatureKit featureKit() throws SdkException {
        throw new UnsupportedOperationException(
                "Feature kit is not supported by this SDK implementation.");
    }
}

package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.model.SystemInfo;

import java.util.List;
import java.util.Map;

/**
 * Read-only diagnostics and observability capabilities of an AQLI runtime.
 *
 * <p>Implementations may expose platform details, system information,
 * recent logs, performance metrics, and point-in-time resource utilization.
 * Default methods retain the SDK's existing graceful fallback behavior.</p>
 */
public interface RuntimeDiagnosticsOperations {

    /**
     * Get information about the underlying hardware and platform.
     */
    default tech.kayys.aqli.plugin.kernel.KernelPlatform getPlatformInfo() throws SdkException {
        return tech.kayys.aqli.plugin.kernel.KernelPlatformDetector.detect();
    }

    SystemInfo getSystemInfo() throws SdkException;

    /**
     * Retrieve recent logs. Returns an empty list when unsupported.
     */
    default List<String> getRecentLogs(int maxLines) throws SdkException {
        return List.of();
    }

    /**
     * Get real-time performance metrics for a provider/model pair.
     *
     * @return Map containing latency, error rate, and resource metrics.
     */
    default Map<String, Object> getMetrics(String providerId, String modelId) throws SdkException {
        return Map.of();
    }

    /**
     * Returns a point-in-time snapshot of CPU and memory utilization.
     *
     * <p>CPU values are in the range [0.0, 1.0]. Memory values are in bytes.</p>
     *
     * @return resource snapshot, or {@link SdkMetricsCollector.ResourceSnapshot#unavailable()}
     *         if not supported by the SDK implementation.
     */
    default SdkMetricsCollector.ResourceSnapshot getResourceSnapshot() throws SdkException {
        return SdkMetricsCollector.ResourceSnapshot.unavailable();
    }
}

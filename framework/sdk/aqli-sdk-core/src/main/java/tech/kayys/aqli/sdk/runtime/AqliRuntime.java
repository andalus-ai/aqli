package tech.kayys.aqli.sdk.runtime;


/**
 * Aggregate interface for AQLI runtime capabilities.
 *
 * <p>New consumers should depend on focused capability ports where possible.
 * This aggregate remains the compatibility entry point and composes inference,
 * model lifecycle, asynchronous jobs, diagnostics, plugin discovery, MCP
 * registry access, and high-level feature operations.</p>
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
        ModelLifecycleOperations, AsyncJobOperations, RuntimeDiagnosticsOperations,
        PluginOperations, McpOperations, FeatureOperations {
}

package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.mcp.McpRegistryManager;

/**
 * Access to the runtime's Model Context Protocol server registry.
 */
public interface McpOperations {

    /**
     * Return the MCP registry manager.
     *
     * @throws UnsupportedOperationException when this runtime has no MCP registry
     */
    default McpRegistryManager mcpRegistry() {
        throw new UnsupportedOperationException(
                "MCP registry is not supported by this SDK implementation");
    }
}

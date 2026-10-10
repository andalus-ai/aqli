package tech.kayys.aqli.sdk.mcp;

public record McpTestEntry(
                String name,
                boolean success,
                int tools,
                int resources,
                int prompts,
                String error) {
}

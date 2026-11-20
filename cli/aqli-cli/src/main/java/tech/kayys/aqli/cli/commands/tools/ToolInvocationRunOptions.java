package tech.kayys.aqli.cli.commands.tools;

import java.util.List;

/** Immutable tool definitions and tool-selection inputs for one run invocation. */
public record ToolInvocationRunOptions(
        List<String> toolFiles,
        String toolChoice,
        List<String> mcpTools) {

    public ToolInvocationRunOptions {
        toolFiles = List.copyOf(toolFiles == null ? List.of() : toolFiles);
        mcpTools = List.copyOf(mcpTools == null ? List.of() : mcpTools);
    }
}

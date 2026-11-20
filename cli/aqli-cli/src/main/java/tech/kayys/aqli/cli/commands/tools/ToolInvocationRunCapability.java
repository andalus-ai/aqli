package tech.kayys.aqli.cli.commands.tools;

import java.util.List;
import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots tool invocation options. */
public final class ToolInvocationRunCapability
        implements RunCapability, RunCapabilityOptions<ToolInvocationRunOptions> {

    @Override
    public String id() {
        return "tool-invocation";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--tool-file", "--tools-file")
                .type(List.class)
                .auxiliaryTypes(String.class)
                .splitRegex(",")
                .description("JSON tool definition file(s); accepts OpenAI-style tools arrays or Aqli ToolDefinition JSON")
                .build());
        commandSpec.addOption(OptionSpec.builder("--tool-choice")
                .type(String.class)
                .description("Tool choice policy such as auto, none, required, or a JSON object")
                .build());
        commandSpec.addOption(OptionSpec.builder("--mcp-tool", "--mcp-tools")
                .type(List.class)
                .auxiliaryTypes(String.class)
                .splitRegex(",")
                .description("MCP tool name(s) to expose, optionally as server/tool or server:tool")
                .build());
    }

    @Override
    public Class<ToolInvocationRunOptions> optionsType() {
        return ToolInvocationRunOptions.class;
    }

    @Override
    public ToolInvocationRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ToolInvocationRunOptions(
                parseResult.matchedOptionValue("--tool-file", List.of()),
                parseResult.matchedOptionValue("--tool-choice", (String) null),
                parseResult.matchedOptionValue("--mcp-tool", List.of()));
    }
}

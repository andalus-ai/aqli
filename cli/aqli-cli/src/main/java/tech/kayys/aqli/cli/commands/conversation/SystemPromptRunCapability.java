package tech.kayys.aqli.cli.commands.conversation;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots the explicit system-prompt option. */
public final class SystemPromptRunCapability
        implements RunCapability, RunCapabilityOptions<SystemPromptRunOptions> {

    @Override
    public String id() {
        return "system-prompt";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--system")
                .type(String.class)
                .description("System prompt")
                .build());
    }

    @Override
    public Class<SystemPromptRunOptions> optionsType() {
        return SystemPromptRunOptions.class;
    }

    @Override
    public SystemPromptRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new SystemPromptRunOptions(
                parseResult.matchedOptionValue("--system", (String) null));
    }
}

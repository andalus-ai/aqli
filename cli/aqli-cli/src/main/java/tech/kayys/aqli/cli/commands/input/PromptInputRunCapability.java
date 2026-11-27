package tech.kayys.aqli.cli.commands.input;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Owns the shared textual prompt input for a run invocation. */
public final class PromptInputRunCapability
        implements RunCapability, RunCapabilityOptions<PromptInputRunOptions> {

    @Override
    public String id() {
        return "prompt-input";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("-p", "--prompt")
                .type(String.class)
                .description("Input prompt")
                .build());
    }

    @Override
    public Class<PromptInputRunOptions> optionsType() {
        return PromptInputRunOptions.class;
    }

    @Override
    public PromptInputRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new PromptInputRunOptions(parseResult.matchedOptionValue("--prompt", (String) null));
    }
}

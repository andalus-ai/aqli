package tech.kayys.aqli.cli.commands.model;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Owns selection of the model identity used by a run invocation. */
public final class ModelSelectionRunCapability
        implements RunCapability, RunCapabilityOptions<ModelSelectionRunOptions> {

    @Override
    public String id() {
        return "model-selection";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("-m", "--model")
                .type(String.class)
                .description("Model ID for repository resolution (e.g., huggingface ID)")
                .build());
    }

    @Override
    public Class<ModelSelectionRunOptions> optionsType() {
        return ModelSelectionRunOptions.class;
    }

    @Override
    public ModelSelectionRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ModelSelectionRunOptions(parseResult.matchedOptionValue("--model", (String) null));
    }
}

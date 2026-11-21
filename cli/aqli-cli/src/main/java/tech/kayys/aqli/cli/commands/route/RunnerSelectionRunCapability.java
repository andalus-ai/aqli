package tech.kayys.aqli.cli.commands.route;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers the requested inference runner route without owning route selection policy. */
public final class RunnerSelectionRunCapability
        implements RunCapability, RunCapabilityOptions<RunnerSelectionRunOptions> {

    @Override
    public String id() {
        return "runner-selection";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--runner")
                .type(String.class)
                .arity("0..1")
                .fallbackValue("auto")
                .description("Runner route: auto, safetensor, gguf, litert, or hybrid. Omit for auto-detection.")
                .build());
    }

    @Override
    public Class<RunnerSelectionRunOptions> optionsType() {
        return RunnerSelectionRunOptions.class;
    }

    @Override
    public RunnerSelectionRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new RunnerSelectionRunOptions(parseResult.matchedOptionValue("--runner", null));
    }
}

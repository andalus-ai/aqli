package tech.kayys.aqli.cli.commands.presentation;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots output-presentation options for one run invocation. */
public final class RunPresentationRunCapability
        implements RunCapability, RunCapabilityOptions<RunPresentationRunOptions> {

    @Override
    public String id() {
        return "run-presentation";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--no-banner")
                .type(boolean.class)
                .defaultValue("false")
                .description("Suppress header and footer output")
                .build());
        commandSpec.addOption(OptionSpec.builder("--no-info")
                .type(boolean.class)
                .defaultValue("false")
                .description("Suppress performance statistic output")
                .build());
        commandSpec.addOption(OptionSpec.builder("--only-text")
                .type(boolean.class)
                .defaultValue("false")
                .description("Only print generated text, equivalent to --no-banner --no-info")
                .build());
        commandSpec.addOption(OptionSpec.builder("--raw")
                .type(boolean.class)
                .defaultValue("false")
                .description("Raw response from inference calculation before json mapping")
                .build());
        commandSpec.addOption(OptionSpec.builder("--debug-prompt")
                .type(boolean.class)
                .defaultValue("false")
                .description("Print raw formatted prompt before dispatch, and raw vs decoded output")
                .build());
    }

    @Override
    public Class<RunPresentationRunOptions> optionsType() {
        return RunPresentationRunOptions.class;
    }

    @Override
    public RunPresentationRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new RunPresentationRunOptions(
                parseResult.matchedOptionValue("--no-banner", false),
                parseResult.matchedOptionValue("--no-info", false),
                parseResult.matchedOptionValue("--only-text", false),
                parseResult.matchedOptionValue("--raw", false),
                parseResult.matchedOptionValue("--debug-prompt", false));
    }
}

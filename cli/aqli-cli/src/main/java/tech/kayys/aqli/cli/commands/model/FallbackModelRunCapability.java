package tech.kayys.aqli.cli.commands.model;

import java.util.List;
import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots fallback model selection options. */
public final class FallbackModelRunCapability
        implements RunCapability, RunCapabilityOptions<FallbackModelRunOptions> {

    @Override
    public String id() {
        return "model-fallback";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--fallback")
                .type(List.class)
                .auxiliaryTypes(String.class)
                .splitRegex(",")
                .description("Comma-separated fallback model IDs or short IDs to try only if the primary model is incompatible")
                .build());
    }

    @Override
    public Class<FallbackModelRunOptions> optionsType() {
        return FallbackModelRunOptions.class;
    }

    @Override
    public FallbackModelRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new FallbackModelRunOptions(
                parseResult.matchedOptionValue("--fallback", List.of()));
    }
}

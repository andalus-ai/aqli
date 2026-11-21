package tech.kayys.aqli.cli.commands.pipeline;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers the explicit feature-pipeline selector for {@code aqli run}. */
public final class FeaturePipelineRunCapability
        implements RunCapability, RunCapabilityOptions<FeaturePipelineRunOptions> {

    @Override
    public String id() {
        return "feature-pipeline";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--pipeline", "--feature")
                .type(String.class)
                .description("Explicit feature pipeline id for out-of-core model orchestration")
                .build());
    }

    @Override
    public Class<FeaturePipelineRunOptions> optionsType() {
        return FeaturePipelineRunOptions.class;
    }

    @Override
    public FeaturePipelineRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new FeaturePipelineRunOptions(parseResult.matchedOptionValue("--pipeline", (String) null));
    }
}

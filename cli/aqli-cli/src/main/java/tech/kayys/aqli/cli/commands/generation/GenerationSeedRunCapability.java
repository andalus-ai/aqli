package tech.kayys.aqli.cli.commands.generation;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers the seed shared by image, audio, and other generation modes. */
public final class GenerationSeedRunCapability
        implements RunCapability, RunCapabilityOptions<GenerationSeedRunOptions> {

    @Override
    public String id() {
        return "generation-seed";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--seed")
                .type(Long.class)
                .description("Random seed for image generation and TTS (MOSS TTS default: 1234)")
                .build());
    }

    @Override
    public Class<GenerationSeedRunOptions> optionsType() {
        return GenerationSeedRunOptions.class;
    }

    @Override
    public GenerationSeedRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new GenerationSeedRunOptions(parseResult.matchedOptionValue("--seed", (Long) null));
    }
}

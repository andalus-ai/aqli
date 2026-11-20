package tech.kayys.aqli.cli.commands.image;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots image-generation options for {@code aqli run}. */
public final class ImageGenerationRunCapability
        implements RunCapability, RunCapabilityOptions<ImageGenerationRunOptions> {

    @Override
    public String id() {
        return "image-generation";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--steps")
                .type(Integer.class)
                .description("Number of denoising steps (default: 20)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--guidance-scale", "--cfg")
                .type(Float.class)
                .description("Classifier-free guidance scale (default: 7.5)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--width")
                .type(Integer.class)
                .description("Output image width in pixels (default: 512, must be multiple of 64)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--height")
                .type(Integer.class)
                .description("Output image height in pixels (default: 512, must be multiple of 64)")
                .build());
    }

    @Override
    public Class<ImageGenerationRunOptions> optionsType() {
        return ImageGenerationRunOptions.class;
    }

    @Override
    public ImageGenerationRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ImageGenerationRunOptions(
                parseResult.matchedOptionValue("--steps", (Integer) null),
                parseResult.matchedOptionValue("--guidance-scale", (Float) null),
                parseResult.matchedOptionValue("--width", (Integer) null),
                parseResult.matchedOptionValue("--height", (Integer) null));
    }
}

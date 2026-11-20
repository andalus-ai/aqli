package tech.kayys.aqli.cli.commands.image;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots image metadata sidecar options. */
public final class ImageMetadataRunCapability
        implements RunCapability, RunCapabilityOptions<ImageMetadataRunOptions> {

    @Override
    public String id() {
        return "image-metadata";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(string("--image-metadata-output",
                "Write image generation metadata JSON to this path"));
        commandSpec.addOption(flag("--image-metadata-sidecar",
                "Write image generation metadata JSON beside the output file"));
        commandSpec.addOption(string("--image-metadata-verify",
                "Verify an image file against an Aqli image metadata JSON sidecar"));
        commandSpec.addOption(string("--image-metadata-refresh",
                "Refresh an Aqli image metadata JSON sidecar with current image size and SHA-256"));
    }

    @Override
    public Class<ImageMetadataRunOptions> optionsType() {
        return ImageMetadataRunOptions.class;
    }

    @Override
    public ImageMetadataRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ImageMetadataRunOptions(
                parseResult.matchedOptionValue("--image-metadata-output", (String) null),
                parseResult.matchedOptionValue("--image-metadata-sidecar", false),
                parseResult.matchedOptionValue("--image-metadata-verify", (String) null),
                parseResult.matchedOptionValue("--image-metadata-refresh", (String) null));
    }

    private static OptionSpec string(String... namesAndDescription) {
        String description = namesAndDescription[namesAndDescription.length - 1];
        String[] names = java.util.Arrays.copyOf(namesAndDescription, namesAndDescription.length - 1);
        return OptionSpec.builder(names).type(String.class).description(description).build();
    }

    private static OptionSpec flag(String... namesAndDescription) {
        String description = namesAndDescription[namesAndDescription.length - 1];
        String[] names = java.util.Arrays.copyOf(namesAndDescription, namesAndDescription.length - 1);
        return OptionSpec.builder(names).type(boolean.class).arity("0").description(description).build();
    }
}

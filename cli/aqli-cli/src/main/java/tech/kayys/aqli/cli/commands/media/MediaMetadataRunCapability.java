package tech.kayys.aqli.cli.commands.media;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots audio and image metadata sidecar options. */
public final class MediaMetadataRunCapability
        implements RunCapability, RunCapabilityOptions<MediaMetadataRunOptions> {

    @Override
    public String id() {
        return "media-metadata";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(string("--audio-metadata-output", "--tts-metadata-output",
                "Write audio generation metadata JSON to this path"));
        commandSpec.addOption(flag("--audio-metadata-sidecar", "--tts-metadata-sidecar",
                "Write audio generation metadata JSON beside the output file"));
        commandSpec.addOption(string("--audio-metadata-verify", "--tts-verify",
                "Verify an audio file against an Aqli audio metadata JSON sidecar"));
        commandSpec.addOption(string("--audio-metadata-refresh", "--tts-refresh-metadata",
                "Refresh an Aqli audio metadata JSON sidecar with current audio size and SHA-256"));
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
    public Class<MediaMetadataRunOptions> optionsType() {
        return MediaMetadataRunOptions.class;
    }

    @Override
    public MediaMetadataRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new MediaMetadataRunOptions(
                parseResult.matchedOptionValue("--audio-metadata-output", (String) null),
                parseResult.matchedOptionValue("--audio-metadata-sidecar", false),
                parseResult.matchedOptionValue("--audio-metadata-verify", (String) null),
                parseResult.matchedOptionValue("--audio-metadata-refresh", (String) null),
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

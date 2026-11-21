package tech.kayys.aqli.cli.commands.media;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers shared output destination and media extension options. */
public final class MediaOutputRunCapability
        implements RunCapability, RunCapabilityOptions<MediaOutputRunOptions> {

    @Override
    public String id() {
        return "media-output";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--output", "-o")
                .type(String.class)
                .description("Output image file path (default: output.png)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--ext")
                .type(String.class)
                .description("Explicit output file extension (mp3, wav, flac, png, jpg, mp4). Overrides extension in --output if provided.")
                .build());
    }

    @Override
    public Class<MediaOutputRunOptions> optionsType() {
        return MediaOutputRunOptions.class;
    }

    @Override
    public MediaOutputRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new MediaOutputRunOptions(
                parseResult.matchedOptionValue("--output", (String) null),
                parseResult.matchedOptionValue("--ext", (String) null));
    }
}

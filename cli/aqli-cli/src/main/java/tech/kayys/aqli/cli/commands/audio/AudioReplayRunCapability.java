package tech.kayys.aqli.cli.commands.audio;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots the audio metadata replay workflow option. */
public final class AudioReplayRunCapability
        implements RunCapability, RunCapabilityOptions<AudioReplayRunOptions> {

    @Override
    public String id() {
        return "audio-replay";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--audio-metadata-replay", "--tts-replay")
                .type(String.class)
                .description("Replay a TTS/audio generation from an Aqli audio metadata JSON sidecar")
                .build());
    }

    @Override
    public Class<AudioReplayRunOptions> optionsType() {
        return AudioReplayRunOptions.class;
    }

    @Override
    public AudioReplayRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new AudioReplayRunOptions(
                parseResult.matchedOptionValue("--audio-metadata-replay", (String) null));
    }
}

package tech.kayys.aqli.cli.commands.tts;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots TTS voice-catalog listing and filtering options. */
public final class TtsVoiceDiscoveryRunCapability
        implements RunCapability, RunCapabilityOptions<TtsVoiceDiscoveryRunOptions> {

    @Override
    public String id() {
        return "tts-voice-discovery";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--list-voices", "--voices", "--tts-voices")
                .type(boolean.class)
                .description("List TTS voice presets for the resolved model and exit")
                .build());
        commandSpec.addOption(OptionSpec.builder("--voices-json", "--tts-voices-json")
                .type(boolean.class)
                .description("List MOSS TTS voice presets as JSON and exit")
                .build());
        commandSpec.addOption(OptionSpec.builder("--voice-search", "--tts-voice-search")
                .type(String.class)
                .description("Search/filter MOSS TTS voices when used with --list-voices")
                .build());
    }

    @Override
    public Class<TtsVoiceDiscoveryRunOptions> optionsType() {
        return TtsVoiceDiscoveryRunOptions.class;
    }

    @Override
    public TtsVoiceDiscoveryRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new TtsVoiceDiscoveryRunOptions(
                parseResult.matchedOptionValue("--list-voices", false),
                parseResult.matchedOptionValue("--voices-json", false),
                parseResult.matchedOptionValue("--voice-search", (String) null));
    }
}

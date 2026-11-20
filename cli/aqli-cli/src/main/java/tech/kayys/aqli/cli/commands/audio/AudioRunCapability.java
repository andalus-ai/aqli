package tech.kayys.aqli.cli.commands.audio;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots audio-generation options for {@code aqli run}. */
public final class AudioRunCapability implements RunCapability, RunCapabilityOptions<AudioRunOptions> {

    @Override
    public String id() {
        return "audio";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        add(commandSpec, string("--voice", "--tts-voice", "TTS voice preset name/id for supported audio models"));
        add(commandSpec, string("--voice-lang", "--voice-language", "--tts-voice-lang", "--tts-voice-language",
                "TTS voice language selector, such as en, jp, zh, english, japanese, or chinese"));
        add(commandSpec, string("--voice-gender", "--tts-voice-gender",
                "TTS voice gender selector, such as male/female. With --voice-lang, non-gender values are treated as voice preset terms."));
        add(commandSpec, string("--tts-codec", "--codec", "MOSS TTS companion audio tokenizer path or local model id"));
        add(commandSpec, flag("--no-tts-auto-codec", "--no-tts-auto-pull-codec",
                "Disable automatic pull of the MOSS TTS companion audio tokenizer"));
        add(commandSpec, integer("--tts-max-frames", "--max-frames", "Maximum TTS audio frames to generate"));
        add(commandSpec, decimal("--tts-max-seconds", "--max-seconds", "--tts-duration",
                "Maximum TTS audio duration in seconds"));
        add(commandSpec, string("--audio-format", "--tts-audio-format",
                "Audio output format for TTS (wav, flac, mp3; opus/aac require the future FFmpeg backend)"));
        add(commandSpec, string("--audio-quality", "--tts-audio-quality",
                "TTS audio quality preset (compact, speech, balanced, high, lossless)"));
        add(commandSpec, integer("--audio-bitrate", "--audio-bitrate-kbps", "--tts-bitrate", "--tts-bitrate-kbps",
                "Encoded TTS audio bitrate in kbps for formats that support it, such as MP3"));
        add(commandSpec, string("--audio-channels", "--tts-audio-channels",
                "TTS audio channel mode (auto, mono, stereo/dual-mono, native)"));
        add(commandSpec, string("--tts-codec-decode", "--codec-decode", "MOSS TTS codec decode mode (auto, streaming, full)"));
        add(commandSpec, flag("--random-seed", "--tts-random-seed",
                "Use a fresh random TTS seed when --seed is omitted"));
        add(commandSpec, flag("--no-audio-polish", "--no-tts-audio-polish",
                "Disable TTS PCM polish (DC removal, edge fades, and peak normalization)"));
        add(commandSpec, flag("--no-audio-normalize", "--no-tts-audio-normalize",
                "Disable TTS peak normalization while keeping other audio polish"));
        add(commandSpec, decimal("--audio-gain-db", "--tts-gain-db",
                "Extra TTS output gain in dB before encoding"));
        add(commandSpec, decimal("--audio-peak-db", "--audio-peak-dbfs", "--tts-peak-db",
                "TTS peak-normalization target in dBFS (default: -3)"));
        add(commandSpec, decimal("--audio-fade-ms", "--tts-fade-ms",
                "TTS fade-in/fade-out duration in milliseconds"));
        add(commandSpec, flag("--audio-trim-silence", "--tts-trim-silence",
                "Trim leading/trailing low-level silence from TTS audio after decode"));
        add(commandSpec, decimal("--audio-trim-db", "--audio-trim-dbfs", "--tts-trim-db",
                "Silence trim threshold in dBFS for TTS audio (default: -48)"));
        add(commandSpec, decimal("--audio-trim-padding-ms", "--tts-trim-padding-ms",
                "Silence trim padding kept at each edge in milliseconds (default: 25)"));
        add(commandSpec, integer("--audio-stream-chunk-kb", "--tts-stream-chunk-kb",
                "Audio chunk size for streamed TTS responses in KiB (default: 64)"));
        add(commandSpec, integer("--tts-stream-progress-frames",
                "Emit a TTS streaming progress update every N generated frames"));
        add(commandSpec, flag("--no-tts-stream-progress",
                "Hide live TTS progress updates during streaming"));
        add(commandSpec, flag("--tts-stream-pcm", "--audio-stream-pcm",
                "Emit decoded PCM chunks while streaming TTS"));
        add(commandSpec, flag("--live-audio", "--tts-live-audio",
                "Play decoded TTS PCM chunks while the final audio file is being generated"));
        add(commandSpec, flag("--no-low-latency-audio", "--no-tts-low-latency-audio",
                "Emit TTS PCM preview chunks after generation instead of during frame generation"));
        add(commandSpec, integer("--flac-compression",
                "FLAC compression level for TTS output, 0 fastest through 8 smallest"));
    }

    @Override
    public Class<AudioRunOptions> optionsType() {
        return AudioRunOptions.class;
    }

    @Override
    public AudioRunOptions snapshot(ParseResult parseResult) {
        return AudioRunOptions.snapshot(parseResult);
    }

    private static OptionSpec string(String... namesAndDescription) {
        return option(namesAndDescription, String.class, false);
    }

    private static OptionSpec integer(String... namesAndDescription) {
        return option(namesAndDescription, Integer.class, false);
    }

    private static OptionSpec decimal(String... namesAndDescription) {
        return option(namesAndDescription, Double.class, false);
    }

    private static OptionSpec flag(String... namesAndDescription) {
        return option(namesAndDescription, boolean.class, true);
    }

    private static OptionSpec option(String[] namesAndDescription, Class<?> type, boolean flag) {
        String description = namesAndDescription[namesAndDescription.length - 1];
        String[] names = java.util.Arrays.copyOf(namesAndDescription, namesAndDescription.length - 1);
        OptionSpec.Builder builder = OptionSpec.builder(names)
                .type(type)
                .description(description);
        if (flag) {
            builder.arity("0");
        }
        return builder.build();
    }

    private static void add(CommandSpec commandSpec, OptionSpec option) {
        commandSpec.addOption(option);
    }
}

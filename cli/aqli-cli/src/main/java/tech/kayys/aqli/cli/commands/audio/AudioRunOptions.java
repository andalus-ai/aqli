package tech.kayys.aqli.cli.commands.audio;

import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/**
 * Immutable snapshot of audio-generation options parsed by the {@code run} command.
 *
 * <p>Workflow flags for listing voices, diagnostics, cache maintenance, and
 * metadata replay remain separate from this generation-options contract.</p>
 */
public record AudioRunOptions(
        String voice,
        String voiceLanguage,
        String voiceGender,
        String voiceSearch,
        String codec,
        boolean disableAutoCodec,
        Integer maxFrames,
        Double maxSeconds,
        String format,
        String quality,
        Integer bitrateKbps,
        String channels,
        String codecDecode,
        boolean randomSeed,
        boolean disablePolish,
        boolean disableNormalize,
        Double gainDb,
        Double peakDb,
        Double fadeMs,
        boolean trimSilence,
        Double trimDbfs,
        Double trimPaddingMs,
        Integer streamChunkKb,
        Integer streamProgressFrames,
        boolean disableStreamProgress,
        boolean streamPcm,
        boolean liveAudio,
        boolean disableLowLatency,
        Integer flacCompression) {

    static AudioRunOptions snapshot(ParseResult parseResult) {
        java.util.Objects.requireNonNull(parseResult, "parseResult");
        return new AudioRunOptions(
                parseResult.matchedOptionValue("--voice", (String) null),
                parseResult.matchedOptionValue("--voice-lang", (String) null),
                parseResult.matchedOptionValue("--voice-gender", (String) null),
                parseResult.matchedOptionValue("--voice-search", (String) null),
                parseResult.matchedOptionValue("--tts-codec", (String) null),
                parseResult.matchedOptionValue("--no-tts-auto-codec", false),
                parseResult.matchedOptionValue("--tts-max-frames", (Integer) null),
                parseResult.matchedOptionValue("--tts-max-seconds", (Double) null),
                parseResult.matchedOptionValue("--audio-format", (String) null),
                parseResult.matchedOptionValue("--audio-quality", (String) null),
                parseResult.matchedOptionValue("--audio-bitrate", (Integer) null),
                parseResult.matchedOptionValue("--audio-channels", (String) null),
                parseResult.matchedOptionValue("--tts-codec-decode", (String) null),
                parseResult.matchedOptionValue("--random-seed", false),
                parseResult.matchedOptionValue("--no-audio-polish", false),
                parseResult.matchedOptionValue("--no-audio-normalize", false),
                parseResult.matchedOptionValue("--audio-gain-db", (Double) null),
                parseResult.matchedOptionValue("--audio-peak-db", (Double) null),
                parseResult.matchedOptionValue("--audio-fade-ms", (Double) null),
                parseResult.matchedOptionValue("--audio-trim-silence", false),
                parseResult.matchedOptionValue("--audio-trim-db", (Double) null),
                parseResult.matchedOptionValue("--audio-trim-padding-ms", (Double) null),
                parseResult.matchedOptionValue("--audio-stream-chunk-kb", (Integer) null),
                parseResult.matchedOptionValue("--tts-stream-progress-frames", (Integer) null),
                parseResult.matchedOptionValue("--no-tts-stream-progress", false),
                parseResult.matchedOptionValue("--tts-stream-pcm", false),
                parseResult.matchedOptionValue("--live-audio", false),
                parseResult.matchedOptionValue("--no-low-latency-audio", false),
                parseResult.matchedOptionValue("--flac-compression", (Integer) null));
    }
}

package tech.kayys.aqli.cli.commands.audio;

import picocli.CommandLine.ParseResult;

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

    public AudioRunOptions withVoice(String value) {
        return new AudioRunOptions(value, voiceLanguage, voiceGender, codec, disableAutoCodec,
                maxFrames, maxSeconds, format, quality, bitrateKbps, channels, codecDecode, randomSeed,
                disablePolish, disableNormalize, gainDb, peakDb, fadeMs, trimSilence, trimDbfs,
                trimPaddingMs, streamChunkKb, streamProgressFrames, disableStreamProgress, streamPcm,
                liveAudio, disableLowLatency, flacCompression);
    }

    /**
     * Applies replay metadata as defaults. Values already supplied on the command
     * line remain authoritative; replay metadata only fills absent values.
     */
    public AudioRunOptions withReplayPlan(AudioMetadataReplayParser.Plan plan) {
        java.util.Objects.requireNonNull(plan, "plan");
        AudioRunOptions result = this;
        for (AudioMetadataReplayParser.ReplayOption replayOption : plan.options()) {
            Object value = replayOption.value();
            result = switch (replayOption.option()) {
                case VOICE -> result.withVoiceIfAbsent((String) value);
                case AUDIO_FORMAT -> result.withFormatIfAbsent((String) value);
                case AUDIO_QUALITY -> result.withQualityIfAbsent((String) value);
                case AUDIO_BITRATE -> result.withBitrateIfAbsent((Integer) value);
                case AUDIO_CHANNELS -> result.withChannelsIfAbsent((String) value);
                case CODEC_DECODE -> result.withCodecDecodeIfAbsent((String) value);
                case MAX_FRAMES -> result.withMaxFramesIfAbsent((Integer) value);
                case MAX_SECONDS -> result.withMaxSecondsIfAbsent((Double) value);
                case GAIN_DB -> result.withGainIfAbsent((Double) value);
                case PEAK_DB -> result.withPeakIfAbsent((Double) value);
                case FADE_MS -> result.withFadeIfAbsent((Double) value);
                case TRIM_DBFS -> result.withTrimDbfsIfAbsent((Double) value);
                case TRIM_PADDING_MS -> result.withTrimPaddingIfAbsent((Double) value);
                case FLAC_COMPRESSION -> result.withFlacCompressionIfAbsent((Integer) value);
                case DISABLE_AUDIO_POLISH -> result.withDisablePolish(true);
                case DISABLE_AUDIO_NORMALIZE -> result.withDisableNormalize(true);
                case TRIM_SILENCE -> result.withTrimSilence(true);
                default -> result;
            };
        }
        return result;
    }

    public String effectiveVoiceSelector() {
        if (voice != null && !voice.isBlank()) {
            return voice.trim();
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (voiceLanguage != null && !voiceLanguage.isBlank()) parts.add(voiceLanguage.trim());
        if (voiceGender != null && !voiceGender.isBlank()) parts.add(voiceGender.trim());
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private AudioRunOptions withVoiceIfAbsent(String value) {
        return absent(voice) && present(value) ? withVoice(value) : this;
    }

    private AudioRunOptions withFormatIfAbsent(String value) {
        return absent(format) && present(value) ? copy(value, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withQualityIfAbsent(String value) {
        return absent(quality) && present(value) ? copy(format, value, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withBitrateIfAbsent(Integer value) {
        return bitrateKbps == null && value != null ? copy(format, quality, value, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withChannelsIfAbsent(String value) {
        return absent(channels) && present(value) ? copy(format, quality, bitrateKbps, value, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withCodecDecodeIfAbsent(String value) {
        return absent(codecDecode) && present(value) ? copy(format, quality, bitrateKbps, channels, value,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withMaxFramesIfAbsent(Integer value) {
        return maxFrames == null && value != null ? copy(format, quality, bitrateKbps, channels, codecDecode,
                value, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withMaxSecondsIfAbsent(Double value) {
        return maxSeconds == null && value != null ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, value, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withGainIfAbsent(Double value) {
        return gainDb == null && value != null ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, value, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withPeakIfAbsent(Double value) {
        return peakDb == null && value != null ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, value, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withFadeIfAbsent(Double value) {
        return fadeMs == null && value != null ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, value, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withTrimDbfsIfAbsent(Double value) {
        return trimDbfs == null && value != null ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, value, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withTrimPaddingIfAbsent(Double value) {
        return trimPaddingMs == null && value != null ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, value, flacCompression,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withFlacCompressionIfAbsent(Integer value) {
        return flacCompression == null && value != null ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, value,
                disablePolish, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withDisablePolish(boolean value) {
        return value && !disablePolish ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                true, disableNormalize, trimSilence) : this;
    }

    private AudioRunOptions withDisableNormalize(boolean value) {
        return value && !disableNormalize ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, true, trimSilence) : this;
    }

    private AudioRunOptions withTrimSilence(boolean value) {
        return value && !trimSilence ? copy(format, quality, bitrateKbps, channels, codecDecode,
                maxFrames, maxSeconds, gainDb, peakDb, fadeMs, trimDbfs, trimPaddingMs, flacCompression,
                disablePolish, disableNormalize, true) : this;
    }

    private AudioRunOptions copy(
            String nextFormat, String nextQuality, Integer nextBitrate, String nextChannels, String nextCodecDecode,
            Integer nextMaxFrames, Double nextMaxSeconds, Double nextGain, Double nextPeak, Double nextFade,
            Double nextTrimDbfs, Double nextTrimPadding, Integer nextFlacCompression,
            boolean nextDisablePolish, boolean nextDisableNormalize, boolean nextTrimSilence) {
        return new AudioRunOptions(voice, voiceLanguage, voiceGender, codec, disableAutoCodec,
                nextMaxFrames, nextMaxSeconds, nextFormat, nextQuality, nextBitrate, nextChannels, nextCodecDecode,
                randomSeed, nextDisablePolish, nextDisableNormalize, nextGain, nextPeak, nextFade,
                nextTrimSilence, nextTrimDbfs, nextTrimPadding, streamChunkKb, streamProgressFrames,
                disableStreamProgress, streamPcm, liveAudio, disableLowLatency, nextFlacCompression);
    }

    private static boolean absent(String value) {
        return value == null || value.isBlank();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}

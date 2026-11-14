package tech.kayys.aqli.cli.commands.audio;

import java.util.Locale;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Maps audio-specific CLI values onto the inference SPI request.
 *
 * <p>This is a CLI adapter: it owns no audio execution behavior and does not
 * define the public SDK capability contract. The SDK/application capability
 * contract remains the target for transport-neutral audio requests.</p>
 */
public final class AudioRunRequestParameterMapper {

    private AudioRunRequestParameterMapper() {
    }

    public record Options(
            String voice,
            String codec,
            Integer maxFrames,
            Double maxSeconds,
            String quality,
            String format,
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
            Double trimThresholdDbfs,
            Double trimPaddingMs,
            Integer streamChunkKb,
            Integer streamProgressFrames,
            boolean disableStreamProgress,
            boolean streamPcm,
            boolean liveAudio,
            boolean disableLowLatency,
            Integer flacCompression) {
    }

    public static void apply(InferenceRequest.Builder request, Options options) {
        if (request == null || options == null) {
            throw new IllegalArgumentException("request and audio options are required");
        }

        addText(request, "voice", options.voice());
        addText(request, "tts_codec", options.codec());
        add(request, "tts_max_frames", options.maxFrames());
        add(request, "tts_max_seconds", options.maxSeconds());

        addNormalizedText(request, "audio_quality", options.quality());
        addText(request, "audio_format", options.format());
        add(request, "audio_bitrate_kbps", options.bitrateKbps());
        addNormalizedText(request, "audio_channels", options.channels());
        addNormalizedText(request, "tts_codec_decode", options.codecDecode());

        if (options.randomSeed()) request.parameter("random_seed", true);
        if (options.disablePolish()) request.parameter("audio_polish", false);
        if (options.disableNormalize()) request.parameter("audio_normalize", false);
        add(request, "audio_gain_db", options.gainDb());
        add(request, "audio_peak_db", options.peakDb());
        add(request, "audio_fade_ms", options.fadeMs());
        if (options.trimSilence()) request.parameter("audio_trim_silence", true);
        add(request, "audio_trim_threshold_dbfs", options.trimThresholdDbfs());
        add(request, "audio_trim_padding_ms", options.trimPaddingMs());
        add(request, "audio_stream_chunk_kb", options.streamChunkKb());
        add(request, "tts_stream_progress_frames", options.streamProgressFrames());

        if (options.disableStreamProgress()) request.parameter("tts_stream_progress", false);
        if (options.streamPcm() || options.liveAudio()) request.parameter("tts_stream_pcm", true);
        if (options.liveAudio()) request.parameter("tts_live_audio", true);
        if (options.disableLowLatency()) request.parameter("tts_low_latency_audio", false);
        add(request, "flac_compression", options.flacCompression());
    }

    private static void addText(InferenceRequest.Builder request, String key, String value) {
        if (value != null && !value.isBlank()) request.parameter(key, value);
    }

    private static void addNormalizedText(InferenceRequest.Builder request, String key, String value) {
        if (value != null && !value.isBlank()) {
            request.parameter(key, value.trim().toLowerCase(Locale.ROOT));
        }
    }

    private static void add(InferenceRequest.Builder request, String key, Object value) {
        if (value != null) request.parameter(key, value);
    }
}

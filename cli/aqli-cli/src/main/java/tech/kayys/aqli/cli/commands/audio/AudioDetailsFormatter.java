package tech.kayys.aqli.cli.commands.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Formats provider audio metadata for the CLI's concise completion summary. */
public final class AudioDetailsFormatter {
    private AudioDetailsFormatter() {
    }

    public static String format(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();

        Double durationSeconds = audioDurationSeconds(metadata);
        if (durationSeconds != null) {
            parts.add(String.format(Locale.ROOT, "%.2fs", durationSeconds));
        }
        Double sampleRate = metadataDouble(metadata, "audio_sample_rate");
        if (sampleRate != null && sampleRate > 0.0) {
            parts.add(String.format(Locale.ROOT, "%d Hz", Math.round(sampleRate)));
        }
        Double channels = metadataDouble(metadata, "audio_channels");
        if (channels != null && channels > 0.0) {
            parts.add(Math.round(channels) + "ch");
        }
        Double bitrateKbps = metadataDouble(metadata, "audio_bitrate_kbps");
        if (bitrateKbps != null && bitrateKbps > 0.0) {
            parts.add(Math.round(bitrateKbps) + " kbps");
        }
        String channelMode = metadataText(metadata, "audio_channel_mode");
        if ("stereo_dual_mono".equalsIgnoreCase(channelMode)) {
            parts.add("dual-mono");
        } else if ("mono_collapsed_duplicate".equalsIgnoreCase(channelMode)) {
            parts.add("mono collapsed");
        }
        if ("true".equalsIgnoreCase(metadataText(metadata, "audio_processing"))) {
            String peak = metadataText(metadata, "audio_processing_peak_after_dbfs");
            parts.add(peak == null || peak.isBlank() ? "polished" : "polished peak " + peak + " dBFS");
        }
        if ("true".equalsIgnoreCase(metadataText(metadata, "audio_processing_trim_silence"))) {
            String removed = metadataText(metadata, "audio_processing_trim_removed_ms");
            parts.add(removed == null || removed.isBlank() ? "trimmed silence" : "trimmed " + removed + " ms");
        }
        Double frames = metadataDouble(metadata, "tts_frames");
        if (frames != null && frames > 0.0) {
            long count = Math.round(frames);
            String frameDetail = count + " " + (count == 1 ? "frame" : "frames");
            String stopReason = metadataText(metadata, "tts_stop_reason");
            if ("max_frames".equalsIgnoreCase(stopReason)) {
                frameDetail += " (frame cap)";
            } else if ("max_seconds".equalsIgnoreCase(stopReason)) {
                frameDetail += " (duration cap)";
            } else if ("max_tokens".equalsIgnoreCase(stopReason)) {
                frameDetail += " (token cap)";
            } else if ("manifest_limit".equalsIgnoreCase(stopReason)) {
                frameDetail += " (model cap)";
            } else if ("stop_token".equalsIgnoreCase(stopReason)) {
                frameDetail += " (natural stop)";
            }
            parts.add(frameDetail);
        }
        String onnxCacheDetail = onnxOptimizedCacheDetail(metadata);
        if (onnxCacheDetail != null) {
            parts.add(onnxCacheDetail);
        }
        String onnxSessionCacheDetail = onnxSessionCacheDetail(metadata);
        if (onnxSessionCacheDetail != null) {
            parts.add(onnxSessionCacheDetail);
        }
        String onnxAssetCacheDetail = onnxAssetCacheDetail(metadata);
        if (onnxAssetCacheDetail != null) {
            parts.add(onnxAssetCacheDetail);
        }
        String voice = metadataText(metadata, "tts_voice");
        if (voice != null && !voice.isBlank()) {
            String voiceDetail = "voice " + voice;
            String voiceMode = metadataText(metadata, "tts_voice_mode");
            String voiceLanguage = metadataText(metadata, "tts_voice_language");
            if ("auto".equalsIgnoreCase(voiceMode)) {
                voiceDetail += voiceLanguage == null || voiceLanguage.isBlank()
                        ? " (auto)"
                        : " (auto " + voiceLanguage + ")";
            } else if ("language".equalsIgnoreCase(voiceMode)) {
                voiceDetail += voiceLanguage == null || voiceLanguage.isBlank()
                        ? " (language)"
                        : " (" + voiceLanguage + " alias)";
            } else if ("selector".equalsIgnoreCase(voiceMode)) {
                voiceDetail += voiceLanguage == null || voiceLanguage.isBlank()
                        ? " (selector)"
                        : " (" + voiceLanguage + " selector)";
            }
            parts.add(voiceDetail);
        }
        String ttsSeed = metadataText(metadata, "tts_seed");
        if (ttsSeed != null && !ttsSeed.isBlank()) {
            String seedSource = metadataText(metadata, "tts_seed_source");
            parts.add("seed " + ttsSeed
                    + ("generated".equalsIgnoreCase(seedSource) ? " (generated)" : ""));
        }
        if ("true".equalsIgnoreCase(metadataText(metadata, "audio_metadata_embedded"))) {
            parts.add("metadata embedded");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String onnxOptimizedCacheDetail(Map<String, Object> metadata) {
        Double sessionValue = metadataDouble(metadata, "onnx_optimized_model_cache_sessions");
        if (sessionValue == null || sessionValue <= 0.0) {
            return null;
        }
        long sessions = Math.round(sessionValue);
        long hits = Math.round(defaultedDouble(metadata, "onnx_optimized_model_cache_hits"));
        long created = Math.round(defaultedDouble(metadata, "onnx_optimized_model_cache_created"));
        long rebuilt = Math.round(defaultedDouble(metadata, "onnx_optimized_model_cache_rebuilt"));
        long disabled = Math.round(defaultedDouble(metadata, "onnx_optimized_model_cache_disabled"));
        if (disabled >= sessions && "false".equalsIgnoreCase(metadataText(metadata, "onnx_optimized_model_cache_enabled"))) {
            return "onnx cache disabled";
        }
        List<String> notes = new ArrayList<>();
        if (created > 0L) {
            notes.add(created + " built");
        }
        if (rebuilt > 0L) {
            notes.add(rebuilt + " rebuilt");
        }
        if (disabled > 0L) {
            notes.add(disabled + " disabled");
        }
        String detail = "onnx cache " + hits + "/" + sessions + " hit";
        return notes.isEmpty() ? detail : detail + " (" + String.join(", ", notes) + ")";
    }

    private static String onnxSessionCacheDetail(Map<String, Object> metadata) {
        String state = metadataText(metadata, "onnx_session_cache_state");
        if (state == null || state.isBlank() || "disabled".equalsIgnoreCase(state)) {
            return null;
        }
        if ("hit".equalsIgnoreCase(state)) {
            return "onnx sessions hot";
        }
        if ("miss".equalsIgnoreCase(state)) {
            return "onnx sessions warmed";
        }
        return null;
    }

    private static String onnxAssetCacheDetail(Map<String, Object> metadata) {
        String state = metadataText(metadata, "onnx_asset_cache_state");
        if (state == null || state.isBlank() || "disabled".equalsIgnoreCase(state)) {
            return null;
        }
        if ("hit".equalsIgnoreCase(state)) {
            return "onnx assets hot";
        }
        if ("miss".equalsIgnoreCase(state)) {
            return "onnx assets warmed";
        }
        return null;
    }

    private static double defaultedDouble(Map<String, Object> metadata, String key) {
        Double value = metadataDouble(metadata, key);
        return value == null ? 0.0 : value;
    }

    private static Double audioDurationSeconds(Map<String, Object> metadata) {
        Double duration = metadataDouble(metadata, "audio_duration_seconds");
        if (duration != null && duration > 0.0) {
            return duration;
        }
        Double samples = metadataDouble(metadata, "audio_samples");
        Double sampleRate = metadataDouble(metadata, "audio_sample_rate");
        if (samples != null && sampleRate != null && samples > 0.0 && sampleRate > 0.0) {
            return samples / sampleRate;
        }
        return null;
    }

    private static Double metadataDouble(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String metadataText(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}

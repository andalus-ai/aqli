package tech.kayys.aqli.cli.commands.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Renders audio-generation timing and throughput metadata for CLI output. */
public final class AudioRunStatsRenderer {
    private AudioRunStatsRenderer() {
    }

    public static boolean isAudioMetadata(Map<String, Object> metadata) {
        return metadata != null
                && (metadata.containsKey("audio_format")
                || metadata.containsKey("audio_duration_seconds")
                || metadata.containsKey("tts_frames"));
    }

    public static Optional<String> render(Map<String, Object> metadata, long fallbackDurationMs) {
        List<String> parts = new ArrayList<>();
        Double audioSeconds = audioDurationSeconds(metadata);
        Double generationSeconds = audioGenerationSeconds(metadata, fallbackDurationMs);
        if (audioSeconds != null && audioSeconds > 0.0) {
            parts.add(String.format(java.util.Locale.ROOT, "Audio: %.2fs", audioSeconds));
        }
        if (generationSeconds != null && generationSeconds > 0.0) {
            parts.add(String.format(java.util.Locale.ROOT, "Generation: %.2fs", generationSeconds));
        }
        Double speed = metadataDouble(metadata, "audio_realtime_speed");
        if (speed == null && audioSeconds != null && generationSeconds != null && generationSeconds > 0.0) {
            speed = audioSeconds / generationSeconds;
        }
        if (speed != null && speed > 0.0) {
            parts.add(String.format(java.util.Locale.ROOT, "Speed: %.2fx realtime", speed));
        }
        Double realtimeFactor = metadataDouble(metadata, "audio_realtime_factor");
        if (realtimeFactor == null && audioSeconds != null && audioSeconds > 0.0
                && generationSeconds != null && generationSeconds > 0.0) {
            realtimeFactor = generationSeconds / audioSeconds;
        }
        if (realtimeFactor != null && realtimeFactor > 0.0) {
            parts.add(String.format(java.util.Locale.ROOT, "RTF: %.2f", realtimeFactor));
        }
        Double framesPerSecond = metadataDouble(metadata, "tts_frames_per_second");
        Double frames = metadataDouble(metadata, "tts_frames");
        if (framesPerSecond == null && frames != null && generationSeconds != null && generationSeconds > 0.0) {
            framesPerSecond = frames / generationSeconds;
        }
        if (framesPerSecond != null && framesPerSecond > 0.0) {
            parts.add(String.format(java.util.Locale.ROOT, "Synthesis: %.1f frames/s", framesPerSecond));
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of("\n[" + String.join(", ", parts) + "]");
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

    private static Double audioGenerationSeconds(Map<String, Object> metadata, long fallbackDurationMs) {
        Double generationSeconds = metadataDouble(metadata, "audio_generation_duration_seconds");
        if (generationSeconds != null && generationSeconds > 0.0) {
            return generationSeconds;
        }
        Double latencyMs = metadataDouble(metadata, "bench.latency_ms");
        if (latencyMs != null && latencyMs > 0.0) {
            return latencyMs / 1000.0;
        }
        return fallbackDurationMs > 0L ? fallbackDurationMs / 1000.0 : null;
    }

    private static Double metadataDouble(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
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
}

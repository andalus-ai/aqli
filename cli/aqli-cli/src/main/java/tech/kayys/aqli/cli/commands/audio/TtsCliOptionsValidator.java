package tech.kayys.aqli.cli.commands.audio;

import java.util.Locale;
import java.util.Set;

/**
 * Validation and preset resolution logic for TTS and audio CLI options.
 */
public final class TtsCliOptionsValidator {

    public record AudioQualityPreset(String format, Integer bitrateKbps, Integer flacCompression) {
    }

    private TtsCliOptionsValidator() {
    }

    public static boolean validateChoice(String option, String value, Set<String> allowed, String displayAllowed) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (allowed.contains(normalized)) {
            return true;
        }
        System.err.println("Error: invalid " + option + " value: " + value);
        System.err.println("Supported values: " + displayAllowed);
        return false;
    }

    public static boolean validateIntRange(String option, int value, int min, int max, String unit) {
        if (value >= min && value <= max) {
            return true;
        }
        String suffix = unit == null || unit.isBlank() ? "" : " " + unit;
        System.err.println("Error: " + option + " must be between " + min + " and " + max + suffix
                + " (got " + value + ").");
        return false;
    }

    public static boolean validateDoubleRange(String option, double value, double min, double max, String unit) {
        if (Double.isFinite(value) && value >= min && value <= max) {
            return true;
        }
        String suffix = unit == null || unit.isBlank() ? "" : " " + unit;
        System.err.println("Error: " + option + " must be between "
                + formatRangeNumber(min) + " and " + formatRangeNumber(max) + suffix
                + " (got " + value + ").");
        return false;
    }

    public static String formatRangeNumber(double value) {
        if (Math.rint(value) == value) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static AudioQualityPreset audioQualityPreset(String audioQuality) {
        if (audioQuality == null || audioQuality.isBlank()) {
            return null;
        }
        return switch (audioQuality.trim().toLowerCase(Locale.ROOT)) {
            case "auto", "default" -> null;
            case "compact", "small" -> new AudioQualityPreset("mp3", 64, null);
            case "speech" -> new AudioQualityPreset("mp3", 96, null);
            case "balanced", "standard" -> new AudioQualityPreset("mp3", 128, null);
            case "high", "quality" -> new AudioQualityPreset("mp3", 192, null);
            case "lossless", "archive", "flac" -> new AudioQualityPreset("flac", null, 8);
            default -> null;
        };
    }

    public static String effectiveAudioFormat(String audioFormat, AudioQualityPreset preset) {
        if (audioFormat != null && !audioFormat.isBlank()) {
            return audioFormat.trim().toLowerCase(Locale.ROOT);
        }
        return preset == null ? null : preset.format();
    }

    public static Integer effectiveAudioBitrateKbps(Integer audioBitrateKbps, AudioQualityPreset preset, String effectiveAudioFormat) {
        if (audioBitrateKbps != null) {
            return audioBitrateKbps;
        }
        if (preset == null || preset.bitrateKbps() == null) {
            return null;
        }
        return "mp3".equalsIgnoreCase(effectiveAudioFormat) ? preset.bitrateKbps() : null;
    }

    public static Integer effectiveFlacCompression(Integer flacCompression, AudioQualityPreset preset, String effectiveAudioFormat) {
        if (flacCompression != null) {
            return flacCompression;
        }
        if (preset == null || preset.flacCompression() == null) {
            return null;
        }
        return "flac".equalsIgnoreCase(effectiveAudioFormat) ? preset.flacCompression() : null;
    }
}

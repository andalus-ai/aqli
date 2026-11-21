package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.cli.commands.audio.AudioMetadataReplayCommandBuilder;
import tech.kayys.aqli.cli.commands.audio.AudioRunOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Assembles reproducible replay arguments and seed values for media sidecars.
 *
 * <p>The replay command builders own argument serialization; this class maps
 * the current run's CLI options and provider metadata into those contracts.</p>
 */
final class MediaMetadataReplayArguments {

    private MediaMetadataReplayArguments() {
    }

    static List<String> audio(
            Path audioPath,
            Map<String, Object> metadata,
            String modelId,
            String providerId,
            String runner,
            String prompt,
            String voiceSelector,
            String defaultExtension,
            String fallbackSeed,
            AudioRunOptions options) {
        return AudioMetadataReplayCommandBuilder.build(new AudioMetadataReplayCommandBuilder.Request(
                modelId,
                providerId,
                runner,
                prompt,
                voiceSelector,
                defaultExtension,
                audioSeed(metadata, fallbackSeed),
                audioPath == null ? null : audioPath.toString(),
                options.quality(),
                options.bitrateKbps(),
                options.channels(),
                options.codecDecode(),
                options.maxFrames(),
                options.maxSeconds(),
                options.gainDb(),
                options.peakDb(),
                options.fadeMs(),
                options.trimDbfs(),
                options.trimPaddingMs(),
                options.flacCompression(),
                options.disablePolish(),
                options.disableNormalize(),
                options.trimSilence()));
    }

    static String audioSeed(Map<String, Object> metadata, String fallbackSeed) {
        return firstPresentMetadataValue(metadata, List.of("tts_seed"), fallbackSeed);
    }

    static String imageSeed(Map<String, Object> metadata, String fallbackSeed) {
        return firstPresentMetadataValue(metadata, List.of("image_seed", "seed", "generation_seed"), fallbackSeed);
    }

    private static String firstPresentMetadataValue(
            Map<String, Object> metadata,
            List<String> keys,
            String fallback) {
        for (String key : keys) {
            String value = metadataText(metadata, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}

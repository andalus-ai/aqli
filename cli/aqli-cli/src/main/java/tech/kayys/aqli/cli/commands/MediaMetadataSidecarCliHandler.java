package tech.kayys.aqli.cli.commands;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for media sidecar CLI operations.
 *
 * <p>Audio and image persistence are implemented by modality-specific handlers.</p>
 */
final class MediaMetadataSidecarCliHandler {
    private MediaMetadataSidecarCliHandler() {
    }

    static boolean verifyAudioMetadataSidecar(String verifyPath, String outputPath) {
        return AudioMetadataSidecarCliHandler.verify(verifyPath, outputPath);
    }

    static boolean refreshAudioMetadataSidecar(String refreshPath, String outputPath) {
        return AudioMetadataSidecarCliHandler.refresh(refreshPath, outputPath);
    }

    static boolean verifyImageMetadataSidecar(String verifyPath, String outputPath) {
        return ImageMetadataSidecarCliHandler.verify(verifyPath, outputPath);
    }

    static boolean refreshImageMetadataSidecar(String refreshPath, String outputPath) {
        return ImageMetadataSidecarCliHandler.refresh(refreshPath, outputPath);
    }

    record AudioSidecarRequest(
            Path audioPath,
            String audioMetadataOutputPath,
            boolean audioMetadataSidecar,
            String modelId,
            String providerId,
            String runner,
            String prompt,
            String voiceSelector,
            String defaultExtension,
            List<String> replayArgs,
            Map<String, Object> metadata
    ) {}

    static void maybeWriteAudioMetadataSidecar(AudioSidecarRequest request) {
        AudioMetadataSidecarCliHandler.write(request);
    }

    record ImageSidecarRequest(
            Path imagePath,
            String imageMetadataOutputPath,
            boolean imageMetadataSidecar,
            String modelId,
            String providerId,
            String runner,
            String prompt,
            String replaySeed,
            Integer steps,
            Object guidanceScale,
            Integer width,
            Integer height,
            Map<String, Object> metadata
    ) {}

    static void maybeWriteImageMetadataSidecar(ImageSidecarRequest request) {
        ImageMetadataSidecarCliHandler.write(request);
    }

    static String imageOutputExtension(Path imagePath, Map<String, Object> metadata) {
        return ImageMetadataSidecarCliHandler.outputExtension(imagePath, metadata);
    }
}

package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.audio.metadata.AudioMetadataSidecarStore;
import tech.kayys.aqli.cli.chat.ChatUIRenderer;
import tech.kayys.aqli.cli.commands.image.ImageMetadataReplayCommandBuilder;
import tech.kayys.aqli.cli.commands.replay.ReplayShellCommandFormatter;
import tech.kayys.aqli.media.metadata.ImageMetadataSidecarStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Handles media sidecar verification, refresh, and serialization for CLI runs.
 */
final class MediaMetadataSidecarCliHandler {

    private MediaMetadataSidecarCliHandler() {
    }

    static boolean verifyAudioMetadataSidecar(String audioMetadataVerifyPath, String outputPath) {
        try {
            Path sidecarPath = Path.of(audioMetadataVerifyPath);
            Path outputOverride = outputPath == null || outputPath.isBlank() ? null : Path.of(outputPath);
            AudioMetadataSidecarStore.VerificationResult result =
                    AudioMetadataSidecarStore.verify(sidecarPath, outputOverride);
            System.out.println(ChatUIRenderer.GREEN + "✓ Audio metadata verified: " + ChatUIRenderer.RESET
                    + result.audioPath());
            System.out.println(ChatUIRenderer.DIM + "Bytes: " + result.sizeBytes()
                    + ", SHA-256: " + result.sha256()
                    + ChatUIRenderer.RESET);
            return true;
        } catch (Exception e) {
            System.err.println("Error: failed to verify audio metadata: " + e.getMessage());
            return false;
        }
    }

    static boolean refreshAudioMetadataSidecar(String audioMetadataRefreshPath, String outputPath) {
        try {
            Path sidecarPath = Path.of(audioMetadataRefreshPath);
            Path outputOverride = outputPath == null || outputPath.isBlank() ? null : Path.of(outputPath);
            AudioMetadataSidecarStore.RefreshResult result =
                    AudioMetadataSidecarStore.refresh(sidecarPath, outputOverride);
            System.out.println(ChatUIRenderer.GREEN + "✓ Audio metadata refreshed: " + ChatUIRenderer.RESET
                    + result.sidecarPath());
            System.out.println(ChatUIRenderer.DIM + "Output: "
                    + result.audioPath()
                    + ", bytes: " + result.sizeBytes()
                    + ", SHA-256: " + result.sha256()
                    + ChatUIRenderer.RESET);
            return true;
        } catch (Exception e) {
            System.err.println("Error: failed to refresh audio metadata: " + e.getMessage());
            return false;
        }
    }

    static boolean verifyImageMetadataSidecar(String imageMetadataVerifyPath, String outputPath) {
        try {
            Path sidecarPath = Path.of(imageMetadataVerifyPath);
            Path outputOverride = outputPath == null || outputPath.isBlank() ? null : Path.of(outputPath);
            ImageMetadataSidecarStore.VerificationResult result =
                    ImageMetadataSidecarStore.verify(sidecarPath, outputOverride);
            System.out.println(ChatUIRenderer.GREEN + "✓ Image metadata verified: " + ChatUIRenderer.RESET
                    + result.imagePath());
            System.out.println(ChatUIRenderer.DIM + "Bytes: " + result.sizeBytes()
                    + ", SHA-256: " + result.sha256()
                    + ChatUIRenderer.RESET);
            return true;
        } catch (ImageMetadataSidecarStore.ImageMetadataSidecarException e) {
            System.err.println(e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error: failed to verify image metadata: " + e.getMessage());
            return false;
        }
    }

    static boolean refreshImageMetadataSidecar(String imageMetadataRefreshPath, String outputPath) {
        try {
            Path sidecarPath = Path.of(imageMetadataRefreshPath);
            Path outputOverride = outputPath == null || outputPath.isBlank() ? null : Path.of(outputPath);
            ImageMetadataSidecarStore.RefreshResult result =
                    ImageMetadataSidecarStore.refresh(sidecarPath, outputOverride);
            System.out.println(ChatUIRenderer.GREEN + "✓ Image metadata refreshed: " + ChatUIRenderer.RESET
                    + result.sidecarPath());
            System.out.println(ChatUIRenderer.DIM + "Output: "
                    + result.imagePath()
                    + ", bytes: " + result.sizeBytes()
                    + ", SHA-256: " + result.sha256()
                    + ChatUIRenderer.RESET);
            return true;
        } catch (ImageMetadataSidecarStore.ImageMetadataSidecarException e) {
            System.err.println(e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error: failed to refresh image metadata: " + e.getMessage());
            return false;
        }
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
        boolean explicitPath = request.audioMetadataOutputPath() != null && !request.audioMetadataOutputPath().isBlank();
        if (!explicitPath && !request.audioMetadataSidecar()) {
            return;
        }
        try {
            Path metadataPath = explicitPath
                    ? Path.of(request.audioMetadataOutputPath())
                    : CliPathUtils.replaceExtension(request.audioPath(), "json");
            AudioMetadataSidecarStore.WriteResult result = AudioMetadataSidecarStore.write(
                    new AudioMetadataSidecarStore.WriteRequest(
                            request.audioPath(),
                            metadataPath,
                            request.modelId(),
                            request.providerId(),
                            request.runner(),
                            request.prompt(),
                            request.voiceSelector(),
                            request.defaultExtension(),
                            request.replayArgs(),
                            ReplayShellCommandFormatter.format(request.replayArgs()),
                            request.metadata()));
            if (result.skippedBecausePathsMatch()) {
                System.err.println("\nWarning: audio metadata path is the same as the audio output; metadata sidecar skipped.");
                return;
            }
            System.out.println(ChatUIRenderer.DIM + "Audio metadata saved to: "
                    + result.sidecarPath()
                    + ChatUIRenderer.RESET);
        } catch (Exception e) {
            System.err.println("\nFailed to save audio metadata: " + e.getMessage());
        }
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
        boolean explicitPath = request.imageMetadataOutputPath() != null && !request.imageMetadataOutputPath().isBlank();
        if (!explicitPath && !request.imageMetadataSidecar()) {
            return;
        }
        try {
            Path metadataPath = explicitPath
                    ? Path.of(request.imageMetadataOutputPath())
                    : CliPathUtils.replaceExtension(request.imagePath(), "json");
            Path absoluteImagePath = request.imagePath().toAbsolutePath().normalize();
            List<String> replayArgs = ImageMetadataReplayCommandBuilder.build(
                    new ImageMetadataReplayCommandBuilder.Request(
                            request.modelId(),
                            request.providerId(),
                            request.runner(),
                            request.prompt(),
                            request.replaySeed(),
                            request.steps(),
                            request.guidanceScale(),
                            request.width(),
                            request.height(),
                            absoluteImagePath.toString()));
            ImageMetadataSidecarStore.WriteResult result = ImageMetadataSidecarStore.write(
                    new ImageMetadataSidecarStore.WriteRequest(
                            absoluteImagePath,
                            metadataPath,
                            request.modelId(),
                            request.providerId(),
                            request.runner(),
                            request.prompt(),
                            imageOutputExtension(absoluteImagePath, request.metadata()),
                            request.replaySeed(),
                            request.steps(),
                            request.guidanceScale(),
                            request.width(),
                            request.height(),
                            replayArgs,
                            ReplayShellCommandFormatter.format(replayArgs),
                            request.metadata()));
            if (result.skippedBecausePathsMatch()) {
                System.err.println("\nWarning: image metadata path is the same as the image output; metadata sidecar skipped.");
                return;
            }
            System.out.println(ChatUIRenderer.DIM + "Image metadata saved to: "
                    + result.sidecarPath()
                    + ChatUIRenderer.RESET);
        } catch (Exception e) {
            System.err.println("\nFailed to save image metadata: " + e.getMessage());
        }
    }

    static String imageOutputExtension(Path imagePath, Map<String, Object> metadata) {
        String ext = CliPathUtils.normalizeExtension(CliPathUtils.pathExtension(imagePath));
        if (!ext.isBlank()) {
            return ext;
        }
        String metadataFormat = null;
        if (metadata != null) {
            Object fmt = metadata.get("image_format");
            if (fmt == null) fmt = metadata.get("image_ext");
            if (fmt != null) metadataFormat = String.valueOf(fmt);
        }
        String normalized = CliPathUtils.normalizeExtension(metadataFormat);
        return normalized.isBlank() ? "png" : normalized;
    }
}

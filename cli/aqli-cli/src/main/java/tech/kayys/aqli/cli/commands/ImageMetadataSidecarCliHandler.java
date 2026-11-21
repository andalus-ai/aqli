package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.cli.chat.ChatUIRenderer;
import tech.kayys.aqli.cli.commands.image.ImageMetadataReplayCommandBuilder;
import tech.kayys.aqli.cli.commands.replay.ReplayShellCommandFormatter;
import tech.kayys.aqli.media.metadata.ImageMetadataSidecarStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Owns image metadata sidecar filesystem operations and CLI diagnostics. */
final class ImageMetadataSidecarCliHandler {
    private ImageMetadataSidecarCliHandler() {
    }

    static boolean verify(String verifyPath, String outputPath) {
        try {
            Path sidecarPath = Path.of(verifyPath);
            Path outputOverride = outputPath == null || outputPath.isBlank() ? null : Path.of(outputPath);
            ImageMetadataSidecarStore.VerificationResult result =
                    ImageMetadataSidecarStore.verify(sidecarPath, outputOverride);
            System.out.println(ChatUIRenderer.GREEN + "✓ Image metadata verified: " + ChatUIRenderer.RESET
                    + result.imagePath());
            System.out.println(ChatUIRenderer.DIM + "Bytes: " + result.sizeBytes()
                    + ", SHA-256: " + result.sha256() + ChatUIRenderer.RESET);
            return true;
        } catch (ImageMetadataSidecarStore.ImageMetadataSidecarException e) {
            System.err.println(e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error: failed to verify image metadata: " + e.getMessage());
            return false;
        }
    }

    static boolean refresh(String refreshPath, String outputPath) {
        try {
            Path sidecarPath = Path.of(refreshPath);
            Path outputOverride = outputPath == null || outputPath.isBlank() ? null : Path.of(outputPath);
            ImageMetadataSidecarStore.RefreshResult result =
                    ImageMetadataSidecarStore.refresh(sidecarPath, outputOverride);
            System.out.println(ChatUIRenderer.GREEN + "✓ Image metadata refreshed: " + ChatUIRenderer.RESET
                    + result.sidecarPath());
            System.out.println(ChatUIRenderer.DIM + "Output: " + result.imagePath()
                    + ", bytes: " + result.sizeBytes() + ", SHA-256: " + result.sha256()
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

    static void write(MediaMetadataSidecarCliHandler.ImageSidecarRequest request) {
        boolean explicitPath = request.imageMetadataOutputPath() != null
                && !request.imageMetadataOutputPath().isBlank();
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
                            request.modelId(), request.providerId(), request.runner(), request.prompt(),
                            request.replaySeed(), request.steps(), request.guidanceScale(),
                            request.width(), request.height(), absoluteImagePath.toString()));
            ImageMetadataSidecarStore.WriteResult result = ImageMetadataSidecarStore.write(
                    new ImageMetadataSidecarStore.WriteRequest(
                            absoluteImagePath, metadataPath, request.modelId(), request.providerId(),
                            request.runner(), request.prompt(),
                            outputExtension(absoluteImagePath, request.metadata()), request.replaySeed(),
                            request.steps(), request.guidanceScale(), request.width(), request.height(),
                            replayArgs, ReplayShellCommandFormatter.format(replayArgs), request.metadata()));
            if (result.skippedBecausePathsMatch()) {
                System.err.println("\nWarning: image metadata path is the same as the image output; metadata sidecar skipped.");
                return;
            }
            System.out.println(ChatUIRenderer.DIM + "Image metadata saved to: "
                    + result.sidecarPath() + ChatUIRenderer.RESET);
        } catch (Exception e) {
            System.err.println("\nFailed to save image metadata: " + e.getMessage());
        }
    }

    private static String outputExtension(Path imagePath, Map<String, Object> metadata) {
        String ext = CliPathUtils.normalizeExtension(CliPathUtils.pathExtension(imagePath));
        if (!ext.isBlank()) {
            return ext;
        }
        Object format = metadata == null ? null : metadata.get("image_format");
        if (format == null && metadata != null) {
            format = metadata.get("image_ext");
        }
        String normalized = CliPathUtils.normalizeExtension(format == null ? null : String.valueOf(format));
        return normalized.isBlank() ? "png" : normalized;
    }
}

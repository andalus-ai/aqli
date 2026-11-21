package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.audio.metadata.AudioMetadataSidecarStore;
import tech.kayys.aqli.cli.chat.ChatUIRenderer;
import tech.kayys.aqli.cli.commands.replay.ReplayShellCommandFormatter;

import java.nio.file.Path;

/** Owns audio metadata sidecar filesystem operations and CLI diagnostics. */
final class AudioMetadataSidecarCliHandler {
    private AudioMetadataSidecarCliHandler() {
    }

    static boolean verify(String verifyPath, String outputPath) {
        try {
            Path sidecarPath = Path.of(verifyPath);
            Path outputOverride = outputPath == null || outputPath.isBlank() ? null : Path.of(outputPath);
            AudioMetadataSidecarStore.VerificationResult result =
                    AudioMetadataSidecarStore.verify(sidecarPath, outputOverride);
            System.out.println(ChatUIRenderer.GREEN + "✓ Audio metadata verified: " + ChatUIRenderer.RESET
                    + result.audioPath());
            System.out.println(ChatUIRenderer.DIM + "Bytes: " + result.sizeBytes()
                    + ", SHA-256: " + result.sha256() + ChatUIRenderer.RESET);
            return true;
        } catch (Exception e) {
            System.err.println("Error: failed to verify audio metadata: " + e.getMessage());
            return false;
        }
    }

    static boolean refresh(String refreshPath, String outputPath) {
        try {
            Path sidecarPath = Path.of(refreshPath);
            Path outputOverride = outputPath == null || outputPath.isBlank() ? null : Path.of(outputPath);
            AudioMetadataSidecarStore.RefreshResult result =
                    AudioMetadataSidecarStore.refresh(sidecarPath, outputOverride);
            System.out.println(ChatUIRenderer.GREEN + "✓ Audio metadata refreshed: " + ChatUIRenderer.RESET
                    + result.sidecarPath());
            System.out.println(ChatUIRenderer.DIM + "Output: " + result.audioPath()
                    + ", bytes: " + result.sizeBytes() + ", SHA-256: " + result.sha256()
                    + ChatUIRenderer.RESET);
            return true;
        } catch (Exception e) {
            System.err.println("Error: failed to refresh audio metadata: " + e.getMessage());
            return false;
        }
    }

    static void write(MediaMetadataSidecarCliHandler.AudioSidecarRequest request) {
        boolean explicitPath = request.audioMetadataOutputPath() != null
                && !request.audioMetadataOutputPath().isBlank();
        if (!explicitPath && !request.audioMetadataSidecar()) {
            return;
        }
        try {
            Path metadataPath = explicitPath
                    ? Path.of(request.audioMetadataOutputPath())
                    : CliPathUtils.replaceExtension(request.audioPath(), "json");
            AudioMetadataSidecarStore.WriteResult result = AudioMetadataSidecarStore.write(
                    new AudioMetadataSidecarStore.WriteRequest(
                            request.audioPath(), metadataPath, request.modelId(), request.providerId(),
                            request.runner(), request.prompt(), request.voiceSelector(), request.defaultExtension(),
                            request.replayArgs(), ReplayShellCommandFormatter.format(request.replayArgs()),
                            request.metadata()));
            if (result.skippedBecausePathsMatch()) {
                System.err.println("\nWarning: audio metadata path is the same as the audio output; metadata sidecar skipped.");
                return;
            }
            System.out.println(ChatUIRenderer.DIM + "Audio metadata saved to: "
                    + result.sidecarPath() + ChatUIRenderer.RESET);
        } catch (Exception e) {
            System.err.println("\nFailed to save audio metadata: " + e.getMessage());
        }
    }
}

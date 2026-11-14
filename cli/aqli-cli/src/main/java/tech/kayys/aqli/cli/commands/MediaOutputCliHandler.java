package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.audio.output.StreamingAudioOutputSession;
import tech.kayys.aqli.cli.chat.ChatUIRenderer;
import tech.kayys.aqli.media.output.MediaOutputPathResolver;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Owns the CLI adapter's media artifact completion lifecycle: resolving the output
 * path, persisting buffered output, writing optional metadata sidecars, and opening
 * the completed artifact with the platform viewer.
 *
 * <p>Media formats, integrity, and sidecar schemas remain owned by the media/audio
 * capability modules. This class only coordinates CLI output policy.</p>
 */
final class MediaOutputCliHandler {

    private final String outputPath;
    private final String extension;
    private final Function<Map<String, Object>, String> audioDefaultExtension;
    private final BiConsumer<Path, Map<String, Object>> audioSidecarWriter;
    private final BiConsumer<Path, Map<String, Object>> imageSidecarWriter;

    MediaOutputCliHandler(
            String outputPath,
            String extension,
            Function<Map<String, Object>, String> audioDefaultExtension,
            BiConsumer<Path, Map<String, Object>> audioSidecarWriter,
            BiConsumer<Path, Map<String, Object>> imageSidecarWriter) {
        this.outputPath = outputPath;
        this.extension = extension;
        this.audioDefaultExtension = audioDefaultExtension;
        this.audioSidecarWriter = audioSidecarWriter;
        this.imageSidecarWriter = imageSidecarWriter;
    }

    void handleOutputs(
            ByteArrayOutputStream imageBuffer,
            StreamingAudioOutputSession audioOutput,
            Map<String, Object> metadata) {
        if (imageBuffer.size() > 0) {
            saveBuffer(imageBuffer, "png", "Image", metadata);
        }
        if (audioOutput == null || !audioOutput.hasOutput()) {
            return;
        }
        try {
            StreamingAudioOutputSession.Output output = audioOutput.finish();
            if (output instanceof StreamingAudioOutputSession.FileOutput fileOutput) {
                Path savedPath = fileOutput.path();
                System.out.println("\n" + ChatUIRenderer.GREEN + ChatUIRenderer.BOLD
                        + "✓ Audio saved to: " + ChatUIRenderer.RESET + savedPath.toAbsolutePath());
                printAudioDetails(metadata);
                audioSidecarWriter.accept(savedPath, metadata);
                MediaOutputOpener.openAudio(savedPath);
            } else if (output instanceof StreamingAudioOutputSession.BufferedOutput bufferedOutput) {
                ByteArrayOutputStream memory = new ByteArrayOutputStream();
                memory.write(bufferedOutput.bytes());
                saveBuffer(memory, audioDefaultExtension.apply(metadata), "Audio", metadata);
            }
        } catch (Exception e) {
            System.err.println("\nFailed to save audio: " + e.getMessage());
            closeAudioOutputQuietly(audioOutput);
        }
    }

    Path resolveAudioOutputPath(Map<String, Object> metadata) {
        MediaOutputPathResolver.Resolution output = MediaOutputPathResolver.resolve(
                new MediaOutputPathResolver.Request(
                        outputPath, extension, audioDefaultExtension.apply(metadata), true));
        warnIfAudioExtensionWasOverridden(output);
        return output.path();
    }

    void saveBuffer(ByteArrayOutputStream buffer, String defaultExt, String label, Map<String, Object> metadata) {
        try {
            MediaOutputPathResolver.Resolution output = MediaOutputPathResolver.resolve(
                    new MediaOutputPathResolver.Request(outputPath, extension, defaultExt, "Audio".equals(label)));
            warnIfAudioExtensionWasOverridden(output);
            String resolvedExtension = output.extension();
            Path resolvedPath = output.path();
            Files.write(resolvedPath, buffer.toByteArray());
            System.out.println("\n" + ChatUIRenderer.GREEN + ChatUIRenderer.BOLD + "✓ " + label
                    + " saved to: " + ChatUIRenderer.RESET + resolvedPath.toAbsolutePath());
            if ("Audio".equals(label)) {
                printAudioDetails(metadata);
                audioSidecarWriter.accept(resolvedPath, metadata);
            } else if ("Image".equals(label)) {
                imageSidecarWriter.accept(resolvedPath, metadata);
            }
            if ("png".equals(resolvedExtension)) {
                MediaOutputOpener.openImage(resolvedPath);
            } else {
                MediaOutputOpener.openAudio(resolvedPath);
            }
        } catch (Exception e) {
            System.err.println("\nFailed to save " + label.toLowerCase() + ": " + e.getMessage());
        }
    }

    static void closeAudioOutputQuietly(StreamingAudioOutputSession audioOutput) {
        if (audioOutput == null) {
            return;
        }
        try {
            audioOutput.close();
        } catch (Exception ignored) {
            // Best-effort cleanup after a failed or interrupted stream.
        }
    }

    static void printAudioDetails(Map<String, Object> metadata) {
        String details = tech.kayys.aqli.cli.commands.audio.AudioDetailsFormatter.format(metadata);
        if (details != null) {
            System.out.println(ChatUIRenderer.DIM + "Audio details: " + details + ChatUIRenderer.RESET);
        }
    }

    private static void warnIfAudioExtensionWasOverridden(MediaOutputPathResolver.Resolution output) {
        if (output.requestedExtensionOverridden()) {
            System.err.println("\nWarning: requested --ext " + output.requestedExtension()
                    + ", but this provider returned " + output.extension()
                    + " audio. Saving as ." + output.extension() + " to avoid a mislabeled file.");
        }
    }
}

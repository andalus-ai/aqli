package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.cli.commands.audio.AudioRunOptions;
import tech.kayys.aqli.cli.commands.generation.GenerationSeedRunOptions;
import tech.kayys.aqli.cli.commands.image.ImageGenerationRunOptions;
import tech.kayys.aqli.cli.commands.image.ImageMetadataRunOptions;
import tech.kayys.aqli.cli.commands.media.MediaMetadataRunOptions;

import java.nio.file.Path;
import java.util.Map;

/**
 * Maps the active run configuration into media sidecar persistence requests.
 *
 * <p>Request assembly is kept separate from both the Picocli command and the
 * sidecar handler that performs filesystem operations.</p>
 */
final class MediaMetadataSidecarRequestFactory {

    private MediaMetadataSidecarRequestFactory() {
    }

    static MediaMetadataSidecarCliHandler.AudioSidecarRequest audio(
            Path audioPath,
            Map<String, Object> metadata,
            MediaMetadataRunOptions sidecarOptions,
            String modelId,
            String providerId,
            String runner,
            String prompt,
            String voiceSelector,
            AudioRunOptions audioOptions,
            GenerationSeedRunOptions seedOptions) {
        String defaultExtension = MediaMetadataReplayArguments.audioDefaultExtension(metadata);
        return new MediaMetadataSidecarCliHandler.AudioSidecarRequest(
                audioPath,
                sidecarOptions.audioOutputPath(),
                sidecarOptions.audioSidecar(),
                modelId,
                providerId,
                runner,
                prompt,
                voiceSelector,
                defaultExtension,
                MediaMetadataReplayArguments.audio(
                        audioPath.toAbsolutePath().normalize(),
                        metadata,
                        modelId,
                        providerId,
                        runner,
                        prompt,
                        voiceSelector,
                        defaultExtension,
                        seedOptions.seed() == null ? null : seedOptions.seed().toString(),
                        audioOptions),
                metadata);
    }

    static MediaMetadataSidecarCliHandler.ImageSidecarRequest image(
            Path imagePath,
            Map<String, Object> metadata,
            ImageMetadataRunOptions sidecarOptions,
            String modelId,
            String providerId,
            String runner,
            String prompt,
            ImageGenerationRunOptions imageOptions,
            GenerationSeedRunOptions seedOptions) {
        return new MediaMetadataSidecarCliHandler.ImageSidecarRequest(
                imagePath,
                sidecarOptions.imageOutputPath(),
                sidecarOptions.imageSidecar(),
                modelId,
                providerId,
                runner,
                prompt,
                MediaMetadataReplayArguments.imageSeed(
                        metadata,
                        seedOptions.seed() == null ? null : seedOptions.seed().toString()),
                imageOptions.steps(),
                imageOptions.guidanceScale(),
                imageOptions.width(),
                imageOptions.height(),
                metadata);
    }
}

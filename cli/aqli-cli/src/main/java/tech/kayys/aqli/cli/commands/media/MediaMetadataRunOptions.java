package tech.kayys.aqli.cli.commands.media;

/** Immutable snapshot of audio and image metadata sidecar operations. */
public record MediaMetadataRunOptions(
        String audioOutputPath,
        boolean audioSidecar,
        String audioReplayPath,
        String audioVerifyPath,
        String audioRefreshPath,
        String imageOutputPath,
        boolean imageSidecar,
        String imageVerifyPath,
        String imageRefreshPath) {
}

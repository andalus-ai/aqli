package tech.kayys.aqli.cli.commands.image;

/** Immutable snapshot of image metadata sidecar operations. */
public record ImageMetadataRunOptions(
        String imageOutputPath,
        boolean imageSidecar,
        String imageVerifyPath,
        String imageRefreshPath) {
}

package tech.kayys.aqli.cli.commands.image;

/** Immutable snapshot of image metadata sidecar operations. */
public record ImageMetadataRunOptions(
        String outputPath,
        boolean sidecar,
        String verifyPath,
        String refreshPath) {
}

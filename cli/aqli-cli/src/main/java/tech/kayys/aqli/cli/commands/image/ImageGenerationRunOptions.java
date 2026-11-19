package tech.kayys.aqli.cli.commands.image;

/** Immutable snapshot of image-generation-specific CLI settings. */
public record ImageGenerationRunOptions(
        Integer steps,
        Float guidanceScale,
        Integer width,
        Integer height) {
}

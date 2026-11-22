package tech.kayys.aqli.cli.commands.presentation;

/** Immutable output-presentation preferences for one run invocation. */
public record RunPresentationRunOptions(
        boolean noBanner,
        boolean noInfo,
        boolean onlyText,
        boolean raw,
        boolean debugPrompt) {
}

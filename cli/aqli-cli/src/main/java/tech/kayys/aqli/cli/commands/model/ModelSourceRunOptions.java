package tech.kayys.aqli.cli.commands.model;

/** Immutable explicit local model-source paths for one run invocation. */
public record ModelSourceRunOptions(
        String modelFile,
        String modelDirectory,
        String modelPath) {
}

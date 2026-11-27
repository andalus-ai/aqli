package tech.kayys.aqli.cli.commands.model;

/** Immutable model repository acquisition and explicit local-import options. */
public record ModelRepositoryRunOptions(
        String modelId,
        boolean offline,
        String branch,
        boolean importModel,
        boolean copyModel,
        boolean force) {
}

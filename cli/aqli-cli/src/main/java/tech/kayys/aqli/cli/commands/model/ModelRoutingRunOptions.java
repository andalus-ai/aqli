package tech.kayys.aqli.cli.commands.model;

/** Immutable requested provider and model-format selection for a run. */
public record ModelRoutingRunOptions(
        String providerId,
        String format) {
}

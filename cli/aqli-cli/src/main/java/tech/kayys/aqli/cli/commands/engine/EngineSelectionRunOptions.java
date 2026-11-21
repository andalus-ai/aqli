package tech.kayys.aqli.cli.commands.engine;

/** Immutable engine-selection hints parsed for one run invocation. */
public record EngineSelectionRunOptions(
        String engine,
        String backend,
        boolean javaNative,
        boolean llamaCpp,
        boolean benchmark,
        boolean directSafetensor,
        boolean preferAlternateRuntime,
        String pluginId) {
}

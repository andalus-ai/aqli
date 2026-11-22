package tech.kayys.aqli.cli.commands.model;

/** Immutable checkpoint and GGUF conversion configuration for a run. */
public record ModelConversionRunOptions(
        String convertMode,
        String ggufOutType,
        String ggufQuant,
        boolean forceGguf) {
}

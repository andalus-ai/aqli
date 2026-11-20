package tech.kayys.aqli.cli.commands.tts;

/** Immutable snapshot of operational TTS diagnostics, warmup, and cache-maintenance settings. */
public record TtsOperationsRunOptions(
        boolean diagnostics,
        boolean warmup,
        boolean cachePrune,
        boolean cachePruneApply) {
}

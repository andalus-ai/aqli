package tech.kayys.aqli.cli.commands.runtime;

/** Immutable inference and KV-cache quantization settings for one run invocation. */
public record QuantizationRunOptions(
        String strategy,
        int bits,
        String kvCacheMode) {
}

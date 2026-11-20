package tech.kayys.aqli.cli.commands.model;

import java.util.List;

/** Immutable fallback model references for compatibility-based model resolution. */
public record FallbackModelRunOptions(List<String> modelIds) {

    public FallbackModelRunOptions {
        modelIds = List.copyOf(modelIds == null ? List.of() : modelIds);
    }
}

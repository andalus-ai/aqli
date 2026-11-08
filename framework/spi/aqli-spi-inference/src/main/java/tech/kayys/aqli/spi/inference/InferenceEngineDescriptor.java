package tech.kayys.aqli.spi.inference;

import java.util.Set;

/**
 * Stable, format-neutral metadata exposed by an inference engine.
 *
 * <p>This contract describes what an engine is; it deliberately does not
 * select engines, resolve models, or own engine lifecycle. Runtime composition
 * and routing remain responsibilities of AQLI.</p>
 */
public interface InferenceEngineDescriptor {

    /** Stable identifier used in configuration, diagnostics, and telemetry. */
    String providerId();

    /** Operations implemented by this engine beyond the base infer contract. */
    Set<Capability> capabilities();

    enum Capability {
        STREAMING,
        EMBEDDINGS,
        ASYNC_JOBS
    }
}

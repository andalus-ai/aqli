package tech.kayys.aqli.safetensor.engine.backend;

import tech.kayys.aljabr.safetensor.spi.SafetensorEngine;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Backend-prepared text model handle.
 *
 * <p>This is intentionally backend-owned. Future implementations can attach
 * native handles, backend-resident weights, compiled graph state, or KV/cache
 * allocators here without leaking those details into provider or CLI code.
 */
public record PreparedTextModel(
        String backendId,
        Path modelPath,
        SafetensorEngine.LoadedModel loadedModel,
        TextExecutionBackendCapabilities capabilities) {

    public PreparedTextModel {
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(modelPath, "modelPath");
        Objects.requireNonNull(capabilities, "capabilities");
    }
}

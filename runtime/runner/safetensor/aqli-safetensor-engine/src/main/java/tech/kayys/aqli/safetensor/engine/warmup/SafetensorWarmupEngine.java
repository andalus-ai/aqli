package tech.kayys.aqli.safetensor.engine.warmup;

import tech.kayys.aljabr.spi.inference.LocalInferenceEngine;
import tech.kayys.aljabr.spi.inference.InferenceResponse;
import tech.kayys.aljabr.spi.inference.InferenceRequest;
import tech.kayys.aljabr.spi.model.HealthStatus;
import tech.kayys.aljabr.spi.inference.StreamingInferenceChunk;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

public class SafetensorWarmupEngine implements LocalInferenceEngine {
    public void initialize() {}
    public void shutdown() {}
    public String name() { return "SafetensorWarmupEngine"; }
    public HealthStatus health() { return HealthStatus.healthy("OK"); }
    public Uni<InferenceResponse> infer(InferenceRequest request) { return null; }
    public Multi<StreamingInferenceChunk> stream(InferenceRequest request) { return null; }
}

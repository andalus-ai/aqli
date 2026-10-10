package tech.kayys.aqli.inference.safetensor;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import tech.kayys.aqli.spi.exception.ProviderException;
import tech.kayys.aqli.spi.provider.ProviderCapabilities;
import tech.kayys.aqli.spi.provider.ProviderConfig;
import tech.kayys.aqli.spi.provider.ProviderHealth;
import tech.kayys.aqli.spi.provider.ProviderRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;

interface SafetensorGgufBackend {
    void initialize(ProviderConfig config) throws ProviderException.ProviderInitializationException;

    Uni<InferenceResponse> infer(ProviderRequest request);

    Multi<StreamingInferenceChunk> inferStream(ProviderRequest request);

    Uni<ProviderHealth> health();

    ProviderCapabilities capabilities();
}

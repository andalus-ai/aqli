package tech.kayys.aqli.inference.safetensor;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aqli.spi.exception.ProviderException;
import tech.kayys.aqli.inference.gguf.GGUFProvider;
import tech.kayys.aqli.spi.provider.ProviderCapabilities;
import tech.kayys.aqli.spi.provider.ProviderConfig;
import tech.kayys.aqli.spi.provider.ProviderHealth;
import tech.kayys.aqli.spi.provider.ProviderRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;

@ApplicationScoped
public class GgufSafetensorBackend implements SafetensorGgufBackend {

    @Inject
    GGUFProvider ggufProvider;

    @Override
    public void initialize(ProviderConfig config) throws ProviderException.ProviderInitializationException {
        ggufProvider.initialize(config);
    }

    @Override
    public Uni<InferenceResponse> infer(ProviderRequest request) {
        return ggufProvider.infer(request);
    }

    @Override
    public Multi<StreamingInferenceChunk> inferStream(ProviderRequest request) {
        return ggufProvider.inferStream(request);
    }

    @Override
    public Uni<ProviderHealth> health() {
        return ggufProvider.health();
    }

    @Override
    public ProviderCapabilities capabilities() {
        return ggufProvider.capabilities();
    }
}

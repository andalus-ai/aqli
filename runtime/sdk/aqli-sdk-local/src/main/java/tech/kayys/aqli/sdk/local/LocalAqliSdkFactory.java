package tech.kayys.aqli.sdk.local;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import tech.kayys.aqli.sdk.local.engine.LocalInferenceEngineCatalog;
import tech.kayys.aqli.model.repo.hf.HuggingFaceRepository;

/** CDI composition point for local SDK instances. */
@ApplicationScoped
public class LocalAqliSdkFactory {

    private final LocalInferenceEngineCatalog engines;
    private final LocalModelCatalog models;
    private final HuggingFaceRepository huggingFaceRepository;

    public LocalAqliSdkFactory(LocalInferenceEngineCatalog engines, LocalModelCatalog models) {
        this(engines, models, null);
    }

    @Inject
    public LocalAqliSdkFactory(LocalInferenceEngineCatalog engines, LocalModelCatalog models,
            HuggingFaceRepository huggingFaceRepository) {
        this.engines = engines;
        this.models = models;
        this.huggingFaceRepository = huggingFaceRepository;
    }

    @Produces
    @ApplicationScoped
    public LocalAqliSdk defaultSdk() {
        return create(null);
    }

    public LocalAqliSdk create(SdkConfig config) {
        return new LocalAqliSdk(engines, config, models, huggingFaceRepository);
    }
}

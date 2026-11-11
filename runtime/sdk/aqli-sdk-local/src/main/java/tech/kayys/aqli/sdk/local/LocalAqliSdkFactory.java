package tech.kayys.aqli.sdk.local;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.sdk.local.engine.LocalInferenceEngineCatalog;

/** CDI composition point for local SDK instances. */
@ApplicationScoped
public class LocalAqliSdkFactory {

    private final LocalInferenceEngineCatalog engines;
    private final LocalModelCatalog models;

    @Inject
    public LocalAqliSdkFactory(LocalInferenceEngineCatalog engines, LocalModelCatalog models) {
        this.engines = engines;
        this.models = models;
    }

    @Produces
    @ApplicationScoped
    public AqliSdk defaultSdk() {
        return create(null);
    }

    public LocalAqliSdk create(SdkConfig config) {
        return new LocalAqliSdk(engines, config, models);
    }
}

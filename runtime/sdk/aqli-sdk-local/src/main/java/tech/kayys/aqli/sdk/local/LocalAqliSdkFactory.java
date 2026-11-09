package tech.kayys.aqli.sdk.local;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.local.engine.LocalInferenceEngineCatalog;

/** CDI composition point for local SDK instances. */
@ApplicationScoped
public class LocalAqliSdkFactory {

    private final LocalInferenceEngineCatalog engines;

    @Inject
    public LocalAqliSdkFactory(LocalInferenceEngineCatalog engines) {
        this.engines = engines;
    }

    public LocalAqliSdk create(SdkConfig config) {
        return new LocalAqliSdk(engines, config);
    }
}

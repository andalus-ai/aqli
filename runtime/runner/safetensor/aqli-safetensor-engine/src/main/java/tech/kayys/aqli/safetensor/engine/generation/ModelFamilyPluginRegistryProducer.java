package tech.kayys.aqli.safetensor.engine.generation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.aljabr.spi.model.ModelFamilyPluginRegistry;

@ApplicationScoped
public class ModelFamilyPluginRegistryProducer {

    @Produces
    @ApplicationScoped
    public ModelFamilyPluginRegistry produceRegistry() {
        return ModelFamilyPluginRegistry.global();
    }
}

package tech.kayys.aqli.safetensor.engine.generation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import tech.kayys.aljabr.spi.model.ModelFamilyPlugin;
import tech.kayys.aljabr.spi.model.ModelFamilyPluginRegistry;

@ApplicationScoped
public class ModelFamilyPluginRegistryProducer {

    @Inject
    Instance<ModelFamilyPlugin> plugins;

    void onStart(@Observes StartupEvent ev) {
        produceRegistry();
    }

    @Produces
    @ApplicationScoped
    public ModelFamilyPluginRegistry produceRegistry() {
        ModelFamilyPluginRegistry registry = ModelFamilyPluginRegistry.global();
        if (plugins != null) {
            for (ModelFamilyPlugin p : plugins) {
                registry.register(p);
            }
        }
        return registry;
    }
}

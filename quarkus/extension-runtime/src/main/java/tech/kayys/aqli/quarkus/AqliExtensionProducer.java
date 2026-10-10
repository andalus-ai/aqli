package tech.kayys.aqli.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AqliExtensionProducer {

    @Produces
    @ApplicationScoped
    public AqliIntegrationService aqliIntegrationService(
            @ConfigProperty(name = "aqli.quarkus.enabled", defaultValue = "false") boolean enabled) {
        if (enabled) {
            System.out.println("[AqliExtension] Quarkus integration enabled at runtime");
            return new RealAqliIntegrationService();
        }
        System.out.println("[AqliExtension] Quarkus integration disabled; using Noop adapters");
        return new NoopAqliIntegrationService();
    }
}

interface AqliIntegrationService {
    String name();
    default boolean enabled() { return false; }
}

class RealAqliIntegrationService implements AqliIntegrationService {
    @Override
    public String name() { return "aqli-quarkus-extension-runtime"; }
    @Override
    public boolean enabled() { return true; }
}

class NoopAqliIntegrationService implements AqliIntegrationService {
    @Override
    public String name() { return "noop-aqli-quarkus-extension"; }
}

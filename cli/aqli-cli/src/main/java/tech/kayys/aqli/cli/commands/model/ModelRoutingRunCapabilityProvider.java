package tech.kayys.aqli.cli.commands.model;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for model routing options. */
public final class ModelRoutingRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "model-routing";
    }

    @Override
    public RunCapability create() {
        return new ModelRoutingRunCapability();
    }
}

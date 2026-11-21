package tech.kayys.aqli.cli.commands.model;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for explicit local model sources. */
public final class ModelSourceRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "model-source";
    }

    @Override
    public RunCapability create() {
        return new ModelSourceRunCapability();
    }
}

package tech.kayys.aqli.cli.commands.model;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for model repository options. */
public final class ModelRepositoryRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "model-repository";
    }

    @Override
    public RunCapability create() {
        return new ModelRepositoryRunCapability();
    }
}

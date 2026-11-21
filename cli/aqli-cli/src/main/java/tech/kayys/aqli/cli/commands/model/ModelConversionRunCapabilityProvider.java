package tech.kayys.aqli.cli.commands.model;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for model conversion options. */
public final class ModelConversionRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "model-conversion";
    }

    @Override
    public RunCapability create() {
        return new ModelConversionRunCapability();
    }
}

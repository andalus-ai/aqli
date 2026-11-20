package tech.kayys.aqli.cli.commands.model;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for fallback model selection options. */
public final class FallbackModelRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "model-fallback";
    }

    @Override
    public RunCapability create() {
        return new FallbackModelRunCapability();
    }
}

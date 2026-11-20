package tech.kayys.aqli.cli.commands.image;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for vision-input CLI options. */
public final class VisionInputRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "vision-input";
    }

    @Override
    public RunCapability create() {
        return new VisionInputRunCapability();
    }
}

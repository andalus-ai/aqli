package tech.kayys.aqli.cli.commands.text;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for the built-in text-generation CLI capability. */
public final class TextRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "text";
    }

    @Override
    public RunCapability create() {
        return new TextRunCapability();
    }
}

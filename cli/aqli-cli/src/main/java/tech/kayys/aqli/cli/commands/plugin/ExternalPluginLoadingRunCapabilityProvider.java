package tech.kayys.aqli.cli.commands.plugin;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for external plugin loading options. */
public final class ExternalPluginLoadingRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "external-plugin-loading";
    }

    @Override
    public RunCapability create() {
        return new ExternalPluginLoadingRunCapability();
    }
}

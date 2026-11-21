package tech.kayys.aqli.cli.commands.runtime;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for runtime profile options. */
public final class RuntimeProfileRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "runtime-profile";
    }

    @Override
    public RunCapability create() {
        return new RuntimeProfileRunCapability();
    }
}

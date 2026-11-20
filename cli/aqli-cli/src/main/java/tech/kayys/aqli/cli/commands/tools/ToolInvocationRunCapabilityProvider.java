package tech.kayys.aqli.cli.commands.tools;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for tool invocation options. */
public final class ToolInvocationRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "tool-invocation";
    }

    @Override
    public RunCapability create() {
        return new ToolInvocationRunCapability();
    }
}

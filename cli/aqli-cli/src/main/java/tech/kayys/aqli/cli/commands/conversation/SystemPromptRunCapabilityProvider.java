package tech.kayys.aqli.cli.commands.conversation;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for system-prompt options. */
public final class SystemPromptRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "system-prompt";
    }

    @Override
    public RunCapability create() {
        return new SystemPromptRunCapability();
    }
}

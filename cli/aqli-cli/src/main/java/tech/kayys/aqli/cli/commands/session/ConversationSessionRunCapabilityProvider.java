package tech.kayys.aqli.cli.commands.session;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for conversation session options. */
public final class ConversationSessionRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "conversation-session";
    }

    @Override
    public RunCapability create() {
        return new ConversationSessionRunCapability();
    }
}

package tech.kayys.aqli.cli.commands.input;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for shared prompt input options. */
public final class PromptInputRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "prompt-input";
    }

    @Override
    public RunCapability create() {
        return new PromptInputRunCapability();
    }
}

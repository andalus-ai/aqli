package tech.kayys.aqli.cli.commands.retrieval;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for retrieval context options. */
public final class RetrievalContextRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "retrieval-context";
    }

    @Override
    public RunCapability create() {
        return new RetrievalContextRunCapability();
    }
}

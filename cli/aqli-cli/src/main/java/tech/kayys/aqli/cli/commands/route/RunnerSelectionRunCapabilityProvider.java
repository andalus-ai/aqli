package tech.kayys.aqli.cli.commands.route;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for runner-selection options. */
public final class RunnerSelectionRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "runner-selection";
    }

    @Override
    public RunCapability create() {
        return new RunnerSelectionRunCapability();
    }
}

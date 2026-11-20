package tech.kayys.aqli.cli.commands.engine;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for engine-selection CLI options. */
public final class EngineSelectionRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "engine-selection";
    }

    @Override
    public RunCapability create() {
        return new EngineSelectionRunCapability();
    }
}

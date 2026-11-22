package tech.kayys.aqli.cli.commands.presentation;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for run presentation options. */
public final class RunPresentationRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "run-presentation";
    }

    @Override
    public RunCapability create() {
        return new RunPresentationRunCapability();
    }
}

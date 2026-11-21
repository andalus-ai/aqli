package tech.kayys.aqli.cli.commands.pipeline;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for feature-pipeline selection. */
public final class FeaturePipelineRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "feature-pipeline";
    }

    @Override
    public RunCapability create() {
        return new FeaturePipelineRunCapability();
    }
}

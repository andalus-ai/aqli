package tech.kayys.aqli.cli.commands.generation;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for shared generation seed options. */
public final class GenerationSeedRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "generation-seed";
    }

    @Override
    public RunCapability create() {
        return new GenerationSeedRunCapability();
    }
}

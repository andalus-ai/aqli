package tech.kayys.aqli.cli.commands.runtime;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for run quantization options. */
public final class QuantizationRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "quantization";
    }

    @Override
    public RunCapability create() {
        return new QuantizationRunCapability();
    }
}

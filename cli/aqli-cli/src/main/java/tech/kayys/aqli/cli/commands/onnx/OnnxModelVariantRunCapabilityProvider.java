package tech.kayys.aqli.cli.commands.onnx;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for ONNX model-variant selection. */
public final class OnnxModelVariantRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "onnx-model-variant";
    }

    @Override
    public RunCapability create() {
        return new OnnxModelVariantRunCapability();
    }
}

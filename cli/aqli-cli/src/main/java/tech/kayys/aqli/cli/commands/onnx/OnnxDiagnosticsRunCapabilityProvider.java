package tech.kayys.aqli.cli.commands.onnx;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for ONNX diagnostics CLI options. */
public final class OnnxDiagnosticsRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "onnx-diagnostics";
    }

    @Override
    public RunCapability create() {
        return new OnnxDiagnosticsRunCapability();
    }
}

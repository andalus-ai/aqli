package tech.kayys.aqli.cli.commands.onnx;

/** Immutable snapshot of ONNX diagnostics and vision-probe settings. */
public record OnnxDiagnosticsRunOptions(
        boolean diagnostics,
        boolean visionProbe,
        boolean pipelineProbe,
        Integer imageTokens,
        Integer decodeTokens) {
}

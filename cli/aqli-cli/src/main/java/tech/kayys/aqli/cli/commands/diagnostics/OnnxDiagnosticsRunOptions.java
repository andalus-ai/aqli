package tech.kayys.aqli.cli.commands.diagnostics;

/** Immutable snapshot of ONNX diagnostic and vision-probe settings. */
public record OnnxDiagnosticsRunOptions(
        boolean diagnostics,
        boolean visionProbe,
        boolean pipelineProbe,
        Integer probeImageTokens,
        Integer probeDecodeTokens) {
}

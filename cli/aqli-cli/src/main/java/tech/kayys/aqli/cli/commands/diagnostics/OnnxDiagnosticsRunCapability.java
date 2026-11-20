package tech.kayys.aqli.cli.commands.diagnostics;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots ONNX model and vision diagnostic options. */
public final class OnnxDiagnosticsRunCapability
        implements RunCapability, RunCapabilityOptions<OnnxDiagnosticsRunOptions> {

    @Override
    public String id() {
        return "onnx-diagnostics";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--onnx-diagnostics", "--onnx-preflight")
                .type(boolean.class)
                .description("Inspect a local ONNX model/repository and exit")
                .build());
        commandSpec.addOption(OptionSpec.builder("--onnx-vision-probe", "--ocr-vision-probe")
                .type(boolean.class)
                .description("Run PaddleOCR-VL vision_encoder with --image and print output tensor shapes")
                .build());
        commandSpec.addOption(OptionSpec.builder("--onnx-pipeline-probe", "--ocr-pipeline-probe")
                .type(boolean.class)
                .description("Run PaddleOCR-VL vision probe and inspect selected embedding/decoder graph IO")
                .build());
        commandSpec.addOption(OptionSpec.builder("--onnx-probe-image-tokens", "--ocr-probe-image-tokens")
                .type(Integer.class)
                .description("Image embeddings to feed into PaddleOCR decoder probe (default: 64, 0 = all)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--onnx-probe-decode-tokens", "--ocr-probe-decode-tokens")
                .type(Integer.class)
                .description("Greedy tokens to decode in the PaddleOCR pipeline probe (default: 8, 0 = prefill only)")
                .build());
    }

    @Override
    public Class<OnnxDiagnosticsRunOptions> optionsType() {
        return OnnxDiagnosticsRunOptions.class;
    }

    @Override
    public OnnxDiagnosticsRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new OnnxDiagnosticsRunOptions(
                parseResult.matchedOptionValue("--onnx-diagnostics", false),
                parseResult.matchedOptionValue("--onnx-vision-probe", false),
                parseResult.matchedOptionValue("--onnx-pipeline-probe", false),
                parseResult.matchedOptionValue("--onnx-probe-image-tokens", (Integer) null),
                parseResult.matchedOptionValue("--onnx-probe-decode-tokens", (Integer) null));
    }
}

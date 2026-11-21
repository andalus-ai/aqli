package tech.kayys.aqli.cli.commands.onnx;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers ONNX graph-variant selection independently of diagnostic mode. */
public final class OnnxModelVariantRunCapability
        implements RunCapability, RunCapabilityOptions<OnnxModelVariantRunOptions> {

    @Override
    public String id() {
        return "onnx-model-variant";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--onnx-variant", "--onnx-precision")
                .type(String.class)
                .description("Preferred ONNX graph variant for multi-graph models (auto, q4, q8, full, uint8)")
                .build());
    }

    @Override
    public Class<OnnxModelVariantRunOptions> optionsType() {
        return OnnxModelVariantRunOptions.class;
    }

    @Override
    public OnnxModelVariantRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new OnnxModelVariantRunOptions(
                parseResult.matchedOptionValue("--onnx-variant", (String) null));
    }
}

package tech.kayys.aqli.cli.commands.runtime;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots inference and KV-cache quantization options. */
public final class QuantizationRunCapability
        implements RunCapability, RunCapabilityOptions<QuantizationRunOptions> {

    @Override
    public String id() {
        return "quantization";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--quantize")
                .type(String.class)
                .description("Enable JIT quantization during inference (bnb, turbo, awq, gptq, autoround)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--quantize-bits")
                .type(int.class)
                .defaultValue("4")
                .description("Bit width for JIT quantization (default: 4)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--quantize-kv")
                .type(String.class)
                .defaultValue("none")
                .description("Enable KV cache quantization (none, int8, int4, turbo)")
                .build());
    }

    @Override
    public Class<QuantizationRunOptions> optionsType() {
        return QuantizationRunOptions.class;
    }

    @Override
    public QuantizationRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new QuantizationRunOptions(
                parseResult.matchedOptionValue("--quantize", (String) null),
                parseResult.matchedOptionValue("--quantize-bits", 4),
                parseResult.matchedOptionValue("--quantize-kv", "none"));
    }
}

package tech.kayys.aqli.cli.commands.model;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers model conversion configuration options. */
public final class ModelConversionRunCapability
        implements RunCapability, RunCapabilityOptions<ModelConversionRunOptions> {

    @Override
    public String id() {
        return "model-conversion";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--gguf")
                .type(boolean.class)
                .defaultValue("false")
                .description("Force GGUF conversion and usage")
                .build());
        commandSpec.addOption(OptionSpec.builder("--convert-mode")
                .type(String.class)
                .defaultValue("auto")
                .description("Checkpoint conversion mode: auto or off")
                .build());
        commandSpec.addOption(OptionSpec.builder("--gguf-outtype")
                .type(String.class)
                .description("GGUF converter outtype (e.g. f16, q8_0, f32)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--gguf-quant")
                .type(String.class)
                .description("Specify quantization for GGUF conversion (e.g. Q4_K_M)")
                .build());
    }

    @Override
    public Class<ModelConversionRunOptions> optionsType() {
        return ModelConversionRunOptions.class;
    }

    @Override
    public ModelConversionRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ModelConversionRunOptions(
                parseResult.matchedOptionValue("--convert-mode", "auto"),
                parseResult.matchedOptionValue("--gguf-outtype", null),
                parseResult.matchedOptionValue("--gguf-quant", null),
                parseResult.matchedOptionValue("--gguf", false));
    }
}

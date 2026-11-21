package tech.kayys.aqli.cli.commands.model;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers requested provider and model-format selection options. */
public final class ModelRoutingRunCapability
        implements RunCapability, RunCapabilityOptions<ModelRoutingRunOptions> {

    @Override
    public String id() {
        return "model-routing";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--provider")
                .type(String.class)
                .arity("0..1")
                .fallbackValue("")
                .description("Provider: native, litert, llamacpp, safetensor, libtorch(experimental), gemini, openai, anthropic, cerebras. Omit for auto-detection.")
                .build());
        commandSpec.addOption(OptionSpec.builder("--format")
                .type(String.class)
                .description("Preferred model format (e.g., safetensors, gguf, onnx)")
                .build());
    }

    @Override
    public Class<ModelRoutingRunOptions> optionsType() {
        return ModelRoutingRunOptions.class;
    }

    @Override
    public ModelRoutingRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ModelRoutingRunOptions(
                parseResult.matchedOptionValue("--provider", null),
                parseResult.matchedOptionValue("--format", null));
    }
}

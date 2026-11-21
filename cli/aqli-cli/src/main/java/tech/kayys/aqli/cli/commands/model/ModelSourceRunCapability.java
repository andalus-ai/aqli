package tech.kayys.aqli.cli.commands.model;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers explicit local model file and directory source options. */
public final class ModelSourceRunCapability
        implements RunCapability, RunCapabilityOptions<ModelSourceRunOptions> {

    @Override
    public String id() {
        return "model-source";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--modelFile")
                .type(String.class)
                .description("Path to a local model file (.gguf, .tflite, .task, .litertlm)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--modelDir")
                .type(String.class)
                .description("Path to a local model directory (Safetensors)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--model-path")
                .type(String.class)
                .description("Path to a custom model file (bypasses repository lookup, prefer --modelFile)")
                .build());
    }

    @Override
    public Class<ModelSourceRunOptions> optionsType() {
        return ModelSourceRunOptions.class;
    }

    @Override
    public ModelSourceRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ModelSourceRunOptions(
                parseResult.matchedOptionValue("--modelFile", (String) null),
                parseResult.matchedOptionValue("--modelDir", (String) null),
                parseResult.matchedOptionValue("--model-path", (String) null));
    }
}

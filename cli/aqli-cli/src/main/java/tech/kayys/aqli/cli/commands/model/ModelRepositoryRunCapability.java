package tech.kayys.aqli.cli.commands.model;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers model repository acquisition and local-import options. */
public final class ModelRepositoryRunCapability
        implements RunCapability, RunCapabilityOptions<ModelRepositoryRunOptions> {

    @Override
    public String id() {
        return "model-repository";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--offline", "--local")
                .type(boolean.class)
                .description("Force using existing models without checking for updates/downloads")
                .build());
        commandSpec.addOption(OptionSpec.builder("-b", "--branch")
                .type(String.class)
                .description("HuggingFace branch/revision (e.g. main, fp16, onnx)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--import")
                .type(boolean.class)
                .description("Import (move) the model file/dir into the aqli model repository (~/.aqli/models/)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--copy")
                .type(boolean.class)
                .description("Copy the model file/dir into the aqli model repository (~/.aqli/models/)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--force")
                .type(boolean.class)
                .description("Force re-download and replace existing model repository files")
                .build());
    }

    @Override
    public Class<ModelRepositoryRunOptions> optionsType() {
        return ModelRepositoryRunOptions.class;
    }

    @Override
    public ModelRepositoryRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ModelRepositoryRunOptions(
                parseResult.hasMatchedOption("--offline") || parseResult.hasMatchedOption("--local"),
                parseResult.matchedOptionValue("--branch", null),
                parseResult.hasMatchedOption("--import"),
                parseResult.hasMatchedOption("--copy"),
                parseResult.hasMatchedOption("--force"));
    }
}

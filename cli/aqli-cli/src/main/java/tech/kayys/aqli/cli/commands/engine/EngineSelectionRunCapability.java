package tech.kayys.aqli.cli.commands.engine;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots engine selection hints for one run invocation. */
public final class EngineSelectionRunCapability
        implements RunCapability, RunCapabilityOptions<EngineSelectionRunOptions> {

    @Override
    public String id() {
        return "engine-selection";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--engine", "--gguf-engine")
                .type(String.class)
                .description("GGUF engine mode: auto, java, llamacpp, benchmark")
                .build());
        commandSpec.addOption(OptionSpec.builder("--backend")
                .type(String.class)
                .description("GGUF backend to use for local standalone runner (metal or cpu)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--java-native", "--java")
                .type(boolean.class)
                .description("Use the Java-native GGUF loader/probe path")
                .build());
        commandSpec.addOption(OptionSpec.builder("--llamacpp", "--llama-cpp")
                .type(boolean.class)
                .description("Use the llama.cpp GGUF engine")
                .build());
        commandSpec.addOption(OptionSpec.builder("--benchmark", "--bench")
                .type(boolean.class)
                .description("Compare Java-native GGUF probe with llama.cpp fallback")
                .build());
        commandSpec.addOption(OptionSpec.builder("--plugin")
                .type(String.class)
                .description("Explicit plugin/engine to use (e.g. llamacpp, java, bnb)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--prefer-alternate-runtime")
                .type(boolean.class)
                .description("Allow compatible model checkpoints to route to local companion runtime artifacts")
                .build());
    }

    @Override
    public Class<EngineSelectionRunOptions> optionsType() {
        return EngineSelectionRunOptions.class;
    }

    @Override
    public EngineSelectionRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new EngineSelectionRunOptions(
                parseResult.matchedOptionValue("--engine", null),
                parseResult.matchedOptionValue("--backend", null),
                parseResult.matchedOptionValue("--java-native", false),
                parseResult.matchedOptionValue("--llamacpp", false),
                parseResult.matchedOptionValue("--benchmark", false),
                parseResult.matchedOptionValue("--prefer-alternate-runtime", false),
                parseResult.matchedOptionValue("--plugin", null));
    }
}

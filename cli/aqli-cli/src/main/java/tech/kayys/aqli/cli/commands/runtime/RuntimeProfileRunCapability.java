package tech.kayys.aqli.cli.commands.runtime;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots runtime profiling options. */
public final class RuntimeProfileRunCapability
        implements RunCapability, RunCapabilityOptions<RuntimeProfileRunOptions> {

    @Override
    public String id() {
        return "runtime-profile";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--profile", "--onnx-profile")
                .type(boolean.class)
                .defaultValue("false")
                .description("Request runner profile metadata and print detailed runtime performance breakdowns")
                .build());
    }

    @Override
    public Class<RuntimeProfileRunOptions> optionsType() {
        return RuntimeProfileRunOptions.class;
    }

    @Override
    public RuntimeProfileRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new RuntimeProfileRunOptions(
                parseResult.matchedOptionValue("--profile", false));
    }
}

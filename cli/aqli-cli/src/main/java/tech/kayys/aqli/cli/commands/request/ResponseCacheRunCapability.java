package tech.kayys.aqli.cli.commands.request;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers the response-cache bypass option for a run. */
public final class ResponseCacheRunCapability
        implements RunCapability, RunCapabilityOptions<ResponseCacheRunOptions> {

    @Override
    public String id() {
        return "response-cache";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--no-cache")
                .type(boolean.class)
                .description("Bypass response cache")
                .build());
    }

    @Override
    public Class<ResponseCacheRunOptions> optionsType() {
        return ResponseCacheRunOptions.class;
    }

    @Override
    public ResponseCacheRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ResponseCacheRunOptions(parseResult.matchedOptionValue("--no-cache", false));
    }
}

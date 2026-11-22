package tech.kayys.aqli.cli.commands.response;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots response streaming and encoding options. */
public final class ResponseDeliveryRunCapability
        implements RunCapability, RunCapabilityOptions<ResponseDeliveryRunOptions> {

    @Override
    public String id() {
        return "response-delivery";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("-s", "--stream")
                .type(boolean.class)
                .defaultValue("true")
                .description("Stream output")
                .build());
        commandSpec.addOption(OptionSpec.builder("--json")
                .type(boolean.class)
                .defaultValue("false")
                .description("Enable JSON output mode")
                .build());
        commandSpec.addOption(OptionSpec.builder("--enable-json")
                .type(boolean.class)
                .defaultValue("false")
                .description("Emit OpenAI-compatible SSE JSON for streamed chunks")
                .build());
    }

    @Override
    public Class<ResponseDeliveryRunOptions> optionsType() {
        return ResponseDeliveryRunOptions.class;
    }

    @Override
    public ResponseDeliveryRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ResponseDeliveryRunOptions(
                parseResult.matchedOptionValue("--stream", true),
                parseResult.matchedOptionValue("--json", false),
                parseResult.matchedOptionValue("--enable-json", false));
    }
}

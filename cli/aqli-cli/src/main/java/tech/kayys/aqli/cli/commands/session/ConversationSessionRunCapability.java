package tech.kayys.aqli.cli.commands.session;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots persistent conversation session options. */
public final class ConversationSessionRunCapability
        implements RunCapability, RunCapabilityOptions<ConversationSessionRunOptions> {

    @Override
    public String id() {
        return "conversation-session";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--session-id")
                .type(String.class)
                .description("Persistent conversation session id for KV/prefix reuse across repeated run calls")
                .build());
    }

    @Override
    public Class<ConversationSessionRunOptions> optionsType() {
        return ConversationSessionRunOptions.class;
    }

    @Override
    public ConversationSessionRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new ConversationSessionRunOptions(
                parseResult.matchedOptionValue("--session-id", (String) null));
    }
}

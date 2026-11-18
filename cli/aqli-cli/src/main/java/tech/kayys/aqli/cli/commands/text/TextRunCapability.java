package tech.kayys.aqli.cli.commands.text;

import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/**
 * Text-generation CLI capability and typed options snapshot contributor.
 *
 * <p>The existing {@link TextRunCliOptions} mixin remains the Picocli binding
 * owner during this migration step. This capability reads the parsed invocation
 * and produces the immutable value consumed by text request mapping.</p>
 */
public final class TextRunCapability implements RunCapability, RunCapabilityOptions<TextRunOptions> {

    @Override
    public String id() {
        return "text";
    }

    @Override
    public void registerOptions(picocli.CommandLine.Model.CommandSpec commandSpec) {
        // TextRunCliOptions is currently registered as a RunCommand mixin.
        // Moving registration here is a separate step to avoid duplicate flags.
    }

    @Override
    public Class<TextRunOptions> optionsType() {
        return TextRunOptions.class;
    }

    @Override
    public TextRunOptions snapshot(ParseResult parseResult) {
        java.util.Objects.requireNonNull(parseResult, "parseResult");
        return new TextRunOptions(
                parseResult.matchedOptionValue("--temperature", 0.2d),
                parseResult.matchedOptionValue("--top-p", 0.9d),
                parseResult.matchedOptionValue("--top-k", 40),
                parseResult.matchedOptionValue("--repeat-penalty", 1.1d),
                parseResult.matchedOptionValue("--json", false),
                parseResult.matchedOptionValue("--max-tokens", 2048),
                parseResult.matchedOptionValue("--mirostat", 0),
                parseResult.matchedOptionValue("--grammar", (String) null));
    }
}

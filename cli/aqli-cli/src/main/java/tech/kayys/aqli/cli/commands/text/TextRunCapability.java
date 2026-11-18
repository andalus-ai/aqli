package tech.kayys.aqli.cli.commands.text;

import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Text-generation CLI capability and typed options snapshot contributor. */
public final class TextRunCapability implements RunCapability, RunCapabilityOptions<TextRunOptions> {

    @Override
    public String id() {
        return "text";
    }

    @Override
    public void registerOptions(picocli.CommandLine.Model.CommandSpec commandSpec) {
        java.util.Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(picocli.CommandLine.Model.OptionSpec.builder("--temperature")
                .type(double.class).defaultValue("0.2")
                .description("Sampling temperature").build());
        commandSpec.addOption(picocli.CommandLine.Model.OptionSpec.builder("--top-p")
                .type(double.class).defaultValue("0.9")
                .description("Top-p sampling").build());
        commandSpec.addOption(picocli.CommandLine.Model.OptionSpec.builder("--top-k")
                .type(int.class).defaultValue("40")
                .description("Top-k sampling").build());
        commandSpec.addOption(picocli.CommandLine.Model.OptionSpec.builder("--repeat-penalty")
                .type(double.class).defaultValue("1.1")
                .description("Repeat penalty").build());
        commandSpec.addOption(picocli.CommandLine.Model.OptionSpec.builder("--max-tokens")
                .type(int.class).defaultValue("2048")
                .description("Maximum tokens to generate").build());
        commandSpec.addOption(picocli.CommandLine.Model.OptionSpec.builder("--mirostat")
                .type(int.class).defaultValue("0")
                .description("Mirostat mode (0, 1, 2)").build());
        commandSpec.addOption(picocli.CommandLine.Model.OptionSpec.builder("--grammar")
                .type(String.class)
                .description("GBNF grammar string").build());
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

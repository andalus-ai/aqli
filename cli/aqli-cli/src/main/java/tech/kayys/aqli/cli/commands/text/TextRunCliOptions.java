package tech.kayys.aqli.cli.commands.text;

import picocli.CommandLine.Option;

/**
 * Picocli-owned settings for text generation on {@code aqli run}.
 *
 * <p>This mutable type is the command-line binding boundary. Request assembly
 * should consume an immutable {@link TextRunOptions} snapshot instead of
 * retaining this parser-facing object.</p>
 */
public final class TextRunCliOptions {

    @Option(names = { "--temperature" }, description = "Sampling temperature", defaultValue = "0.2")
    public double temperature;

    @Option(names = { "--top-p" }, description = "Top-p sampling", defaultValue = "0.9")
    public double topP;

    @Option(names = { "--top-k" }, description = "Top-k sampling", defaultValue = "40")
    public int topK;

    @Option(names = { "--repeat-penalty" }, description = "Repeat penalty", defaultValue = "1.1")
    public double repeatPenalty;

    @Option(names = { "--json" }, description = "Enable JSON mode", defaultValue = "false")
    public boolean jsonMode;

    @Option(names = { "--max-tokens" }, description = "Maximum tokens to generate", defaultValue = "2048")
    public int maxTokens;

    @Option(names = { "--mirostat" }, description = "Mirostat mode (0, 1, 2)", defaultValue = "0")
    public int mirostat;

    @Option(names = { "--grammar" }, description = "GBNF grammar string")
    public String grammar;

    public TextRunOptions snapshot() {
        return new TextRunOptions(
                temperature, topP, topK, repeatPenalty, jsonMode, maxTokens, mirostat, grammar);
    }
}

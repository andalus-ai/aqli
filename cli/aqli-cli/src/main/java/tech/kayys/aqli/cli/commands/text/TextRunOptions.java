package tech.kayys.aqli.cli.commands.text;

/**
 * Immutable snapshot of text-generation CLI settings used during request assembly.
 *
 * <p>Picocli field ownership remains in the command adapter during this migration.
 * This value object provides a stable hand-off for text-specific request mapping.</p>
 */
public record TextRunOptions(
        double temperature,
        double topP,
        int topK,
        double repeatPenalty,
        boolean jsonMode,
        int maxTokens,
        int mirostat,
        String grammar) {
}

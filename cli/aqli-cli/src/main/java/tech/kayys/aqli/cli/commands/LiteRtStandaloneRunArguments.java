package tech.kayys.aqli.cli.commands;

/**
 * Builds the argument vector for the LiteRT standalone {@code run} command.
 *
 * <p>This is a CLI-to-runner contract; it does not resolve models or decide
 * whether the standalone route is eligible.</p>
 */
final class LiteRtStandaloneRunArguments {

    private LiteRtStandaloneRunArguments() {
    }

    static String[] toArgs(
            String modelFile,
            String prompt,
            int maxTokens,
            double temperature,
            int topK,
            double topP) {
        return new String[] {
                "run",
                "--modelFile", modelFile,
                "--prompt", prompt,
                "--max-tokens", Integer.toString(maxTokens),
                "--temperature", Double.toString(temperature),
                "--top-k", Integer.toString(topK),
                "--top-p", Double.toString(topP),
                "--provider", "litert"
        };
    }
}

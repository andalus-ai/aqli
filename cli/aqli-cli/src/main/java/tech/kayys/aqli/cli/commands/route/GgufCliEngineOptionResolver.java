package tech.kayys.aqli.cli.commands.route;

/**
 * Resolves explicit GGUF engine flags into the engine hint passed to runtime routing.
 *
 * <p>This is a CLI option-precedence policy only; it does not select or validate a runtime.</p>
 */
public final class GgufCliEngineOptionResolver {

    private GgufCliEngineOptionResolver() {
    }

    public static String resolve(
            boolean benchmark,
            boolean javaNative,
            boolean llamaCpp,
            String engine) {
        if (benchmark) {
            return "benchmark";
        }
        if (javaNative) {
            return "java";
        }
        if (llamaCpp) {
            return "llamacpp";
        }
        return engine == null ? null : engine.trim();
    }
}

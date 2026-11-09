package tech.kayys.aqli.inference.gguf;

import java.util.Locale;

/**
 * Immutable, CLI-independent representation of the options consumed by the
 * GGUF fast execution path.
 *
 * <p>The caller supplies configuration-derived defaults. This keeps process
 * configuration and environment access in the CLI/runtime composition layer,
 * while the runner owns interpretation and validation of its arguments.</p>
 */
public record GgufRunArguments(
        String model,
        String modelFile,
        String prompt,
        int maxTokens,
        double temperature,
        double topP,
        int topK,
        double repeatPenalty,
        String provider,
        String backend,
        String engine,
        boolean banner,
        boolean noInfo,
        boolean raw,
        boolean debugPrompt,
        boolean verbose,
        boolean optionParsingSupported) {

    public static GgufRunArguments parse(
            String[] args,
            String defaultBackend,
            String defaultEngine) {
        Builder parsed = new Builder(defaultBackend, defaultEngine);
        if (args == null || args.length == 0 || !"run".equals(args[0])) {
            parsed.optionParsingSupported = false;
            return parsed.build();
        }

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            String value = null;
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 0) {
                value = arg.substring(eq + 1);
                arg = arg.substring(0, eq);
            }
            switch (arg) {
                case "--model", "-m" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.model = next.value();
                    i = next.index();
                }
                case "--modelFile", "--model-file", "--model-path" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.modelFile = next.value();
                    i = next.index();
                }
                case "--prompt", "-p" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.prompt = next.value();
                    i = next.index();
                }
                case "--provider" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.provider = next.value();
                    i = next.index();
                }
                case "--backend", "--platform" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.backend = next.value();
                    i = next.index();
                }
                case "--gguf-engine", "--engine" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.engine = next.value();
                    i = next.index();
                }
                case "--java-native", "--java" -> parsed.engine = "java";
                case "--llamacpp", "--llama-cpp" -> parsed.engine = "llamacpp";
                case "--benchmark", "--bench" -> parsed.engine = "benchmark";
                case "--no-banner", "--suppress-banner" -> parsed.banner = false;
                case "-v", "--verbose" -> parsed.verbose = true;
                case "--no-info" -> parsed.noInfo = true;
                case "--raw" -> parsed.raw = true;
                case "--debug-prompt" -> parsed.debugPrompt = true;
                case "--only-text" -> {
                    parsed.banner = false;
                    parsed.noInfo = true;
                }
                case "--use-cpu" -> parsed.backend = "cpu";
                case "--max-tokens" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.maxTokens = parseInt(next.value(), parsed.maxTokens);
                    i = next.index();
                }
                case "--temperature" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.temperature = parseDouble(next.value(), parsed.temperature);
                    i = next.index();
                }
                case "--top-p" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.topP = parseDouble(next.value(), parsed.topP);
                    i = next.index();
                }
                case "--top-k" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.topK = parseInt(next.value(), parsed.topK);
                    i = next.index();
                }
                case "--repeat-penalty", "--repeat_penalty", "--repetition-penalty" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.repeatPenalty = parseDouble(next.value(), parsed.repeatPenalty);
                    i = next.index();
                }
                default -> {
                    if (arg.startsWith("--")) {
                        parsed.optionParsingSupported = false;
                    }
                }
            }
        }

        parsed.maxTokens = Math.max(1, parsed.maxTokens);
        parsed.topK = Math.max(1, parsed.topK);
        parsed.topP = Math.max(0.0d, Math.min(1.0d, parsed.topP));
        parsed.temperature = Math.max(0.0d, parsed.temperature);
        return parsed.build();
    }

    /** Whether the fast path has enough required values and a recognized provider. */
    public boolean supported() {
        if (!optionParsingSupported || prompt == null || prompt.isBlank()) {
            return false;
        }
        if ((model == null || model.isBlank()) && (modelFile == null || modelFile.isBlank())) {
            return false;
        }
        if (provider == null || provider.isBlank()) {
            return true;
        }
        String normalized = provider.toLowerCase(Locale.ROOT);
        return normalized.equals("gguf")
                || normalized.equals("native")
                || normalized.equals("llamacpp")
                || normalized.equals("llama.cpp")
                || normalized.equals("llama-cpp")
                || normalized.equals("java")
                || normalized.equals("java-native")
                || normalized.equals("jvm");
    }

    public boolean hasNonGgufProvider() {
        return provider != null && !provider.isBlank() && !supported();
    }

    private static Value valueOrNext(String value, String[] args, int index) {
        if (value != null) {
            return new Value(value, index);
        }
        if (index + 1 >= args.length) {
            return new Value("", index);
        }
        return new Value(args[index + 1], index + 1);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private record Value(String value, int index) {
    }

    private static final class Builder {
        private String model;
        private String modelFile;
        private String prompt;
        private int maxTokens = 2048;
        private double temperature = 0.2d;
        private double topP = 0.9d;
        private int topK = 40;
        private double repeatPenalty = Double.NaN;
        private String provider;
        private String backend;
        private String engine;
        private boolean banner = true;
        private boolean noInfo;
        private boolean raw;
        private boolean debugPrompt;
        private boolean verbose;
        private boolean optionParsingSupported = true;

        private Builder(String backend, String engine) {
            this.backend = backend;
            this.engine = engine;
        }

        private GgufRunArguments build() {
            return new GgufRunArguments(
                    model, modelFile, prompt, maxTokens, temperature, topP, topK,
                    repeatPenalty, provider, backend, engine, banner, noInfo, raw,
                    debugPrompt, verbose, optionParsingSupported);
        }
    }
}

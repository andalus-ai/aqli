package tech.kayys.aqli.provider.litert;

import java.util.Locale;

/**
 * Immutable command-line argument contract for the lightweight LiteRT-LM
 * standalone execution path. CLI-specific environment defaults are supplied
 * by the caller so this contract remains independent of Picocli and Quarkus.
 */
public record LiteRTRunArguments(
        String model,
        String modelFile,
        String prompt,
        int maxTokens,
        double temperature,
        double topP,
        int topK,
        String backend,
        boolean supported,
        boolean banner) {

    public static LiteRTRunArguments parse(String[] args, String defaultBackend) {
        Builder parsed = new Builder(defaultBackend);
        if (args == null || args.length == 0 || !"run".equals(args[0])) {
            parsed.supported = false;
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
                case "-v", "--verbose" -> { /* verbosity is a CLI process concern */ }
                case "--no-banner", "--suppress-banner" -> parsed.banner = false;
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
                case "--backend" -> {
                    Value next = valueOrNext(value, args, i);
                    parsed.backend = next.value();
                    i = next.index();
                }
                case "--use-cpu" -> parsed.backend = "cpu";
                case "--enable-cpu" -> { /* CPU fallback is already enabled */ }
                case "--platform" -> {
                    Value next = valueOrNext(value, args, i);
                    String platform = next.value();
                    i = next.index();
                    if ("cpu".equalsIgnoreCase(platform)) {
                        parsed.backend = "cpu";
                    } else if ("metal".equalsIgnoreCase(platform) || "gpu".equalsIgnoreCase(platform)) {
                        parsed.backend = "gpu";
                    } else {
                        parsed.supported = false;
                    }
                }
                case "--provider" -> {
                    Value next = valueOrNext(value, args, i);
                    String provider = next.value();
                    i = next.index();
                    if (provider != null && !provider.isBlank() && !"litert".equalsIgnoreCase(provider)) {
                        parsed.supported = false;
                    }
                }
                case "--format" -> {
                    Value next = valueOrNext(value, args, i);
                    String format = next.value();
                    i = next.index();
                    if (format != null && !format.isBlank() && !isLiteRtFormat(format)) {
                        parsed.supported = false;
                    }
                }
                case "--engine", "--gguf-engine" -> {
                    Value next = valueOrNext(value, args, i);
                    i = next.index();
                }
                case "--stream" -> {
                    String stream = value != null ? value : "true";
                    if ("false".equalsIgnoreCase(stream)) parsed.supported = false;
                }
                case "--no-cache", "--offline", "--local" -> { /* safe local-path no-ops */ }
                default -> {
                    if (arg.startsWith("-")) parsed.supported = false;
                }
            }
        }
        return parsed.build();
    }

    public boolean isSupportedRequest() {
        return supported && prompt != null && !prompt.isBlank()
                && ((model != null && !model.isBlank()) || (modelFile != null && !modelFile.isBlank()));
    }

    private static boolean isLiteRtFormat(String format) {
        if (format == null || format.isBlank()) return false;
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("litert") || normalized.equals("litertlm")
                || normalized.equals("tflite") || normalized.equals("tfl") || normalized.equals("task");
    }

    private static Value valueOrNext(String inline, String[] args, int currentIndex) {
        if (inline != null) return new Value(inline, currentIndex);
        int nextIndex = currentIndex + 1;
        if (nextIndex >= args.length) return new Value("", currentIndex);
        return new Value(args[nextIndex], nextIndex);
    }

    private static int parseInt(String value, int fallback) {
        try { return Math.max(1, Integer.parseInt(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); }
        catch (Exception ignored) { return fallback; }
    }

    private record Value(String value, int index) {}

    private static final class Builder {
        private String model;
        private String modelFile;
        private String prompt;
        private int maxTokens = 256;
        private double temperature = 0.2d;
        private double topP = 0.9d;
        private int topK = 40;
        private String backend;
        private boolean supported = true;
        private boolean banner = true;

        private Builder(String defaultBackend) {
            backend = defaultBackend;
        }

        private LiteRTRunArguments build() {
            return new LiteRTRunArguments(model, modelFile, prompt, maxTokens, temperature,
                    topP, topK, backend, supported, banner);
        }
    }
}

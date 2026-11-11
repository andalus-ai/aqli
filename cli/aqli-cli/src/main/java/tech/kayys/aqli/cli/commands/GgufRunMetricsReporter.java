package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.observability.ObservabilityManager;
import tech.kayys.aqli.metrics.MetricsRegistry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Formats standalone GGUF run metrics and publishes their telemetry. */
final class GgufRunMetricsReporter {
    private GgufRunMetricsReporter() {
    }

    static void printFastRunStats(
            PrintStream out,
            String label,
            int outputTokens,
            long startNanos,
            long openNanos,
            long generateNanos,
            long endNanos) {
        printFastRunStats(out, label, outputTokens, startNanos, openNanos, generateNanos, endNanos,
                Optional.empty());
    }

    static void printFastRunStats(
            PrintStream out,
            String label,
            int outputTokens,
            long startNanos,
            long openNanos,
            long generateNanos,
            long endNanos,
            Optional<String> nativeMetrics) {
        long durationNanos = Math.max(0L, endNanos - startNanos);
        long durationMs = Duration.ofNanos(durationNanos).toMillis();
        double durationSeconds = durationMs / 1000.0d;
        double totalSpeed = durationSeconds > 0.0d && outputTokens > 0 ? outputTokens / durationSeconds : 0.0d;
        double generateMs = openOrGenerateMillis(generateNanos);
        double generateSeconds = generateNanos / 1_000_000_000.0d;
        double generationSpeed = generateSeconds > 0.0d && outputTokens > 0 ? outputTokens / generateSeconds : 0.0d;
        double tokenLatencyMs = outputTokens > 0 ? generateMs / outputTokens : 0.0d;

        out.printf("%n[%s, Duration: %.2fs, Speed: %.2f t/s (generate call)]%n", label, durationSeconds,
                generationSpeed);
        out.println("Performance Metrics:");
        out.printf("  open time      = %9.2f ms%n", openOrGenerateMillis(openNanos));
        out.printf("  generate call  = %9.2f ms%n", generateMs);
        out.printf("  end-to-end rate= %9.2f t/s (%d output tokens / generate-call time)%n",
                generationSpeed, Math.max(0, outputTokens));
        out.printf("  token latency  = %9.2f ms/token (end-to-end)%n", tokenLatencyMs);
        printNativeMetrics(out, nativeMetrics);
        out.println();

        try {
            ObservabilityManager obs = ObservabilityManager.global();
            if (obs != null) {
                MetricsRegistry reg = obs.getMetricsRegistry();
                AttributesBuilder attrs = Attributes.builder()
                        .put("engine", "gguf")
                        .put("fast_path", true);

                if (nativeMetrics.isPresent()) {
                    Map<String, String> m = parseNativeMetrics(nativeMetrics.get());
                    String backend = m.get("backend");
                    if (backend != null && !backend.isBlank()) {
                        attrs.put("native_backend", backend);
                    }
                    if (m.containsKey("gpuLayers"))
                        attrs.put("gpuLayers", Long.parseLong(m.get("gpuLayers")));
                    if (m.containsKey("threads"))
                        attrs.put("threads", Long.parseLong(m.get("threads")));
                    if (m.containsKey("context"))
                        attrs.put("context", Long.parseLong(m.get("context")));
                    if (m.containsKey("batch"))
                        attrs.put("batch", Long.parseLong(m.get("batch")));

                    if (m.containsKey("modelLoad"))
                        reg.record("aqli.llm.native.load", Double.parseDouble(m.get("modelLoad")), attrs.build());
                    if (m.containsKey("contextInit"))
                        reg.record("aqli.llm.native.context_init", Double.parseDouble(m.get("contextInit")),
                                attrs.build());
                    if (m.containsKey("tokenize"))
                        reg.record("aqli.llm.native.tokenize", Double.parseDouble(m.get("tokenize")), attrs.build());
                    if (m.containsKey("prefill")) {
                        double prefillMs = nativeMillis(m, "prefill");
                        int promptTokens = parseCount(m.get("promptTokens"));
                        reg.record("aqli.llm.native.prefill", prefillMs, attrs.build());
                        reg.record("aqli.llm.prefill.duration", prefillMs, attrs.build());
                        reg.record("aqli.llm.prefill.speed",
                                tokenRatePerSecond(promptTokens, prefillMs), attrs.build());
                    }
                    if (m.containsKey("decode")) {
                        double decodeMs = nativeMillis(m, "decode");
                        int decodedTokens = parseCount(m.get("decodedTokens"));
                        reg.record("aqli.llm.native.decode", decodeMs, attrs.build());
                        reg.record("aqli.llm.decode.duration", decodeMs, attrs.build());
                        reg.record("aqli.llm.decode.speed",
                                tokenRatePerSecond(decodedTokens, decodeMs), attrs.build());
                        reg.record("aqli.llm.decode.token_latency",
                                decodedTokens > 0 ? decodeMs / decodedTokens : 0.0d, attrs.build());
                    }
                }

                Attributes finalAttrs = attrs.build();
                reg.record("aqli.llm.generation.duration", durationMs, finalAttrs);
                reg.record("aqli.llm.generation.speed", totalSpeed, finalAttrs);
                reg.record("aqli.llm.generation.end_to_end_speed", generationSpeed, finalAttrs);
                reg.record("aqli.llm.generation.token_latency", tokenLatencyMs, finalAttrs);
                reg.record("aqli.llm.model.open_time", openOrGenerateMillis(openNanos), finalAttrs);
            }
        } catch (Exception e) {
            // Ignore telemetry exceptions
        }
    }

    private static double openOrGenerateMillis(long nanos) {
        return Math.max(0L, nanos) / 1_000_000.0d;
    }

    private static void printNativeMetrics(PrintStream out, Optional<String> nativeMetrics) {
        if (nativeMetrics.isEmpty()) {
            return;
        }
        Map<String, String> metrics = parseNativeMetrics(nativeMetrics.get());
        if (metrics.isEmpty()) {
            return;
        }
        String backend = metrics.get("backend");
        if (backend != null && !backend.isBlank()) {
            out.printf("  native backend = %s%s%n", backend, nativeRuntimeShape(metrics));
        }
        out.printf("  native load    = %9.2f ms, context init = %.2f ms%n",
                nativeMillis(metrics, "modelLoad"),
                nativeMillis(metrics, "contextInit"));
        out.printf("  native tokenize= %9.2f ms (%s)%n",
                nativeMillis(metrics, "tokenize"),
                metrics.getOrDefault("tokenizeCache", "unknown"));
        double prefillMs = nativeMillis(metrics, "prefill");
        int promptTokens = parseCount(metrics.get("promptTokens"));
        double prefillSpeed = tokenRatePerSecond(promptTokens, prefillMs);
        out.printf("  prompt prefill = %9.2f ms (%d tokens @ %.2f t/s)%n",
                prefillMs, promptTokens, prefillSpeed);
        double decodeMs = nativeMillis(metrics, "decode");
        int decodedTokens = parseCount(metrics.get("decodedTokens"));
        double decodeSpeed = tokenRatePerSecond(decodedTokens, decodeMs);
        double decodeLatencyMs = decodedTokens > 0 ? decodeMs / decodedTokens : 0.0d;
        out.printf("  generation     = %9.2f ms (%s sampled, %d decoded @ %.2f t/s)%n",
                decodeMs,
                metrics.getOrDefault("generatedTokens", "0"),
                decodedTokens, decodeSpeed);
        out.printf("  decode latency = %9.2f ms/decoded token%n", decodeLatencyMs);
        String sampler = metrics.get("sampler");
        if (sampler != null && !sampler.isBlank()) {
            out.printf("  native sampler = %s, %.2f ms%n",
                    sampler,
                    nativeMillis(metrics, "samplerMs"));
        }
        String warmSession = metrics.get("warmSession");
        if (warmSession != null && !warmSession.isBlank()) {
            String reuseKind = metrics.getOrDefault("sessionReuse", "").trim();
            out.printf("  native session = %s%s%n",
                    Boolean.parseBoolean(warmSession) ? "warm" : "cold",
                    reuseKind.isBlank() ? "" : " (" + reuseKind + ")");
        }
        out.printf("  native cache   = %s, %.2f ms%s%n",
                metrics.getOrDefault("promptCache", "unknown"),
                nativeMillis(metrics, "promptCacheMs"),
                nativePromptCacheFlags(metrics));
        printNativeOutputMetrics(out, metrics);
    }

    private static String nativePromptCacheFlags(Map<String, String> metrics) {
        List<String> flags = new ArrayList<>();
        if (Boolean.parseBoolean(metrics.getOrDefault("promptCacheEagerShort", "false"))) {
            flags.add("eager-short");
        }
        if (Boolean.parseBoolean(metrics.getOrDefault("repeatedPrompt", "false"))) {
            flags.add("repeat");
        }
        return flags.isEmpty() ? "" : " (" + String.join(", ", flags) + ")";
    }

    private static void printNativeOutputMetrics(PrintStream out, Map<String, String> metrics) {
        String outputBytes = metrics.get("outputBytes");
        String javaBufferBytes = metrics.get("javaOutputBufferBytes");
        String javaRetries = metrics.get("javaOutputRetries");
        if ((outputBytes == null || outputBytes.isBlank())
                && (javaBufferBytes == null || javaBufferBytes.isBlank())
                && (javaRetries == null || javaRetries.isBlank())) {
            return;
        }
        out.printf("  native output  = %s bytes, java buffer = %s bytes, retries = %s%n",
                outputBytes == null || outputBytes.isBlank() ? "unknown" : outputBytes,
                javaBufferBytes == null || javaBufferBytes.isBlank() ? "unknown" : javaBufferBytes,
                javaRetries == null || javaRetries.isBlank() ? "0" : javaRetries);
    }

    private static String nativeRuntimeShape(Map<String, String> metrics) {
        List<String> parts = new ArrayList<>();
        addNativeRuntimePart(parts, "gpuLayers", metrics.get("gpuLayers"));
        addNativeRuntimePart(parts, "threads", metrics.get("threads"));
        addNativeRuntimePart(parts, "ctx", metrics.get("ctx"));
        addNativeRuntimePart(parts, "batch", metrics.get("batch"));
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
    }

    private static void addNativeRuntimePart(List<String> parts, String name, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(name + "=" + value);
        }
    }

    static Map<String, String> parseNativeMetrics(String rawMetrics) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (rawMetrics == null || rawMetrics.isBlank()) {
            return parsed;
        }
        for (String part : rawMetrics.split(",")) {
            int split = part.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = part.substring(0, split).trim();
            String value = part.substring(split + 1).trim();
            if (!key.isBlank() && !value.isBlank()) {
                parsed.put(key, value);
            }
        }
        return parsed;
    }

    private static int parseCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double tokenRatePerSecond(int tokens, double elapsedMillis) {
        return tokens > 0 && Double.isFinite(elapsedMillis) && elapsedMillis > 0.0d
                ? tokens * 1000.0d / elapsedMillis
                : 0.0d;
    }

    private static double nativeMillis(Map<String, String> metrics, String key) {
        String value = metrics.get(key);
        if (value == null || value.isBlank()) {
            return 0.0d;
        }
        String normalized = value.trim();
        if (normalized.endsWith("ms")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return 0.0d;
        }
    }

}

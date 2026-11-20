package tech.kayys.aqli.cli.commands;

import java.util.Map;

import io.opentelemetry.api.common.Attributes;
import tech.kayys.aqli.metrics.MetricsRegistry;
import tech.kayys.aqli.observability.ObservabilityManager;

/** Records the CLI's completed inference-generation telemetry. */
final class RunResponseTelemetryRecorder {
    private RunResponseTelemetryRecorder() {
    }

    static void record(Map<String, Object> metadata, int outputTokens, long durationMs, double tokensPerSecond) {
        try {
            ObservabilityManager observability = ObservabilityManager.global();
            if (observability == null) {
                return;
            }
            MetricsRegistry registry = observability.getMetricsRegistry();
            Attributes attributes = Attributes.builder()
                    .put("engine", resolveEngine(metadata))
                    .put("fast_path", false)
                    .build();
            registry.record("aqli.llm.generation.duration", durationMs, attributes);
            registry.record("aqli.llm.generation.speed", tokensPerSecond, attributes);
            registry.record("aqli.llm.generation.token_latency", tokenLatencyMillis(durationMs, outputTokens), attributes);
        } catch (Exception ignored) {
            // Telemetry must not affect inference response delivery.
        }
    }

    static String resolveEngine(Map<String, Object> metadata) {
        String engine = metadata.containsKey("engine") ? String.valueOf(metadata.get("engine")) : "standard";
        return metadata.containsKey("runner") ? String.valueOf(metadata.get("runner")) : engine;
    }

    static double tokenLatencyMillis(long durationMs, int outputTokens) {
        return outputTokens > 0 ? (double) durationMs / outputTokens : 0.0d;
    }
}

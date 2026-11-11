package tech.kayys.aqli.cli.runtime;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.spi.observability.AdapterMetricSchema;
import tech.kayys.aljabr.spi.observability.AdapterMetricsRecorder;

@ApplicationScoped
@DefaultBean
public class CliDefaultAljabrAdapterMetricsRecorder implements AdapterMetricsRecorder {

    @Override
    public void recordSuccess(AdapterMetricSchema schema, long durationMs) {
        // CLI default: metrics are optional, so this is intentionally a no-op.
    }

    @Override
    public void recordFailure(AdapterMetricSchema schema, long durationMs, String error) {
        // CLI default: metrics are optional, so this is intentionally a no-op.
    }

    @Override
    public void recordTokens(AdapterMetricSchema schema, int inputTokens, int outputTokens) {
        // CLI default: metrics are optional, so this is intentionally a no-op.
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}

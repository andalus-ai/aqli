package tech.kayys.aqli.metrics;

import io.micrometer.core.instrument.Timer;

public interface MetricsPublisher {
    void recordSuccess(String runnerName, String modelId, long duration);

    void recordFailure(String runnerName, String modelId, String errorType);

    Timer.Sample startTimer();
}
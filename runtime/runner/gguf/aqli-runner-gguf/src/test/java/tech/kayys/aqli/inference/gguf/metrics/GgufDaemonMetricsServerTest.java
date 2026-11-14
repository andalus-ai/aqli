package tech.kayys.aqli.inference.gguf.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GgufDaemonMetricsServerTest {

    @Test
    void servesPrometheusMetricsOnLoopbackAndClosesCleanly() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Object gaugeState = new Object();
        Gauge.builder("aqli.test.daemon", gaugeState, ignored -> 3.0)
                .register(registry);

        try (GgufDaemonMetricsServer server = GgufDaemonMetricsServer.start(
                registry::scrape, registry::close, 0)) {
            assertTrue(server.isLoopbackBound());
            URL metricsUrl = new URL(
                    "http", InetAddress.getLoopbackAddress().getHostAddress(), server.port(), "/metrics");
            HttpURLConnection connection = (HttpURLConnection) metricsUrl.openConnection();
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_000);
            assertEquals(200, connection.getResponseCode());
            String body;
            try (InputStream input = connection.getInputStream()) {
                body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(body.contains("aqli_test_daemon"));
            assertTrue(body.contains("3.0"));
            connection.disconnect();
        }
    }

    @Test
    void rejectsPathsOtherThanMetrics() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try (GgufDaemonMetricsServer server = GgufDaemonMetricsServer.start(
                registry::scrape, registry::close, 0)) {
            URL rootUrl = new URL(
                    "http", InetAddress.getLoopbackAddress().getHostAddress(), server.port(), "/");
            HttpURLConnection connection = (HttpURLConnection) rootUrl.openConnection();
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_000);
            assertEquals(404, connection.getResponseCode());
            connection.disconnect();
        }
    }
}

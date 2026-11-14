package tech.kayys.aqli.inference.gguf.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class GgufDaemonMetricsServerTest {

    @Test
    void servesPrometheusMetricsOnLoopbackAndClosesCleanly() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Gauge.builder("aqli.test.daemon", new Object(), ignored -> 3.0)
                .register(registry);

        try (GgufDaemonMetricsServer server = GgufDaemonMetricsServer.start(registry, 0)) {
            assertTrue(server.isLoopbackBound());
            assertTrue(InetAddress.getLoopbackAddress().isLoopbackAddress());

            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "http://" + InetAddress.getLoopbackAddress().getHostAddress()
                            + ":" + server.port() + "/metrics").openConnection();
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
        assertTrue(registry.isClosed());
    }

    @Test
    void rejectsPathsOtherThanMetrics() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try (GgufDaemonMetricsServer server = GgufDaemonMetricsServer.start(registry, 0)) {
            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "http://" + InetAddress.getLoopbackAddress().getHostAddress()
                            + ":" + server.port() + "/").openConnection();
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_000);
            assertEquals(404, connection.getResponseCode());
            connection.disconnect();
        }
    }
}

package tech.kayys.aqli.inference.gguf.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void rejectsOversizedRequestLinesAndHeaders() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try (GgufDaemonMetricsServer server = GgufDaemonMetricsServer.start(
                registry::scrape, registry::close, 0)) {
            String oversizedRequestLine = "GET /" + "a".repeat(5_000) + " HTTP/1.1\r\n\r\n";
            assertEquals(414, requestStatus(server, oversizedRequestLine));

            String oversizedHeader = "GET /metrics HTTP/1.1\r\n"
                    + "X-Test: " + "a".repeat(5_000) + "\r\n\r\n";
            assertEquals(431, requestStatus(server, oversizedHeader));
        }
    }

    @Test
    void closeTerminatesClientBlockedWhileReadingHeaders() throws Exception {
        AtomicBoolean registryClosed = new AtomicBoolean();
        try (GgufDaemonMetricsServer server = GgufDaemonMetricsServer.start(
                () -> "", () -> registryClosed.set(true), 0);
                Socket client = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            client.setSoTimeout(1_000);
            client.getOutputStream().write("GET /metrics HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().flush();

            assertTrue(awaitActiveClient(server));
            long startedAt = System.nanoTime();
            server.close();

            assertTrue(System.nanoTime() - startedAt < 1_000_000_000L,
                    "closing the server should not wait for the client read timeout");
            assertEquals(-1, client.getInputStream().read());
            assertTrue(registryClosed.get());
        }
    }

    private static boolean awaitActiveClient(GgufDaemonMetricsServer server) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (server.hasActiveClient()) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private static int requestStatus(GgufDaemonMetricsServer server, String request)
            throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(2_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            return Integer.parseInt(response.split(" ", 3)[1]);
        }
    }
}

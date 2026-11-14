package tech.kayys.aqli.inference.gguf.metrics;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import tech.kayys.aqli.inference.gguf.GgufSessionCache;

/**
 * Small loopback-only HTTP endpoint for scraping daemon-owned Prometheus metrics.
 *
 * <p>The endpoint serves only {@code GET /metrics}; it does not expose inference
 * controls or bind to a non-loopback interface. The registry is owned by this
 * server and is closed with it.</p>
 */
public final class GgufDaemonMetricsServer implements AutoCloseable {
    private static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";
    private static final int CLIENT_TIMEOUT_MILLIS = 2_000;

    private final ServerSocket server;
    private final Supplier<String> scrape;
    private final Runnable closeRegistry;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread acceptor;

    private GgufDaemonMetricsServer(
            ServerSocket server, Supplier<String> scrape, Runnable closeRegistry) {
        this.server = server;
        this.scrape = scrape;
        this.closeRegistry = closeRegistry;
        this.acceptor = Thread.ofPlatform()
                .daemon(true)
                .name("aqli-gguf-metrics")
                .start(this::accept);
    }

    public static GgufDaemonMetricsServer start(GgufSessionCache cache, int port)
            throws IOException {
        Objects.requireNonNull(cache, "cache");
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try {
            new GgufSessionCacheMetricsBinder(cache).bindTo(registry);
            return start(registry::scrape, registry::close, port);
        } catch (IOException | RuntimeException exception) {
            registry.close();
            throw exception;
        }
    }

    static GgufDaemonMetricsServer start(
            Supplier<String> scrape, Runnable closeRegistry, int port) throws IOException {
        Objects.requireNonNull(scrape, "scrape");
        Objects.requireNonNull(closeRegistry, "closeRegistry");
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("metrics port must be between 0 and 65535");
        }
        ServerSocket server = new ServerSocket(port, 16, InetAddress.getLoopbackAddress());
        try {
            return new GgufDaemonMetricsServer(server, scrape, closeRegistry);
        } catch (RuntimeException exception) {
            try {
                server.close();
            } catch (IOException ignored) {
                // Preserve the startup failure.
            }
            throw exception;
        }
    }

    public int port() {
        return server.getLocalPort();
    }

    public boolean isLoopbackBound() {
        return server.getInetAddress().isLoopbackAddress();
    }

    private void accept() {
        while (running.get()) {
            try (Socket client = server.accept()) {
                client.setSoTimeout(CLIENT_TIMEOUT_MILLIS);
                serve(client);
            } catch (IOException | RuntimeException exception) {
                if (running.get()) {
                    // A malformed or disconnected scrape must not stop inference.
                }
            }
        }
    }

    private void serve(Socket client) throws IOException {
        BufferedReader request = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
        String requestLine = request.readLine();
        if (requestLine == null) {
            return;
        }
        String[] parts = requestLine.split(" ", 3);
        String method = parts.length > 0 ? parts[0] : "";
        String path = parts.length > 1 ? parts[1] : "";
        String line;
        while ((line = request.readLine()) != null && !line.isEmpty()) {
            // Consume headers before writing the response.
        }

        if (!"GET".equals(method)) {
            respond(client, 405, "Method Not Allowed", "method not allowed\n");
        } else if (!"/metrics".equals(path)) {
            respond(client, 404, "Not Found", "not found\n");
        } else {
            respond(client, 200, "OK", scrape.get());
        }
    }

    private static void respond(Socket client, int status, String reason, String body)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + CONTENT_TYPE + "\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n\r\n";
        client.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().write(payload);
        client.getOutputStream().flush();
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            server.close();
        } catch (IOException ignored) {
            // Shutdown is best-effort; the acceptor is daemon-owned.
        }
        try {
            acceptor.join(CLIENT_TIMEOUT_MILLIS + 500L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        closeRegistry.run();
    }
}

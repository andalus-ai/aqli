package tech.kayys.aqli.inference.gguf.metrics;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private static final int MAX_REQUEST_LINE_BYTES = 4_096;
    private static final int MAX_HEADER_LINE_BYTES = 4_096;
    private static final int MAX_HEADER_BYTES = 16_384;
    private static final int MAX_HEADER_COUNT = 64;

    private final ServerSocket server;
    private final Supplier<String> scrape;
    private final Runnable closeRegistry;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Object clientLifecycleLock = new Object();
    private final Thread acceptor;
    private Socket activeClient;

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
            Socket client;
            try {
                client = server.accept();
            } catch (IOException | RuntimeException exception) {
                if (running.get()) {
                    // A listener failure must not affect inference.
                }
                return;
            }

            synchronized (clientLifecycleLock) {
                if (!running.get()) {
                    closeQuietly(client);
                    return;
                }
                activeClient = client;
            }

            try (client) {
                client.setSoTimeout(CLIENT_TIMEOUT_MILLIS);
                serve(client);
            } catch (IOException | RuntimeException exception) {
                if (running.get()) {
                    // A malformed or disconnected scrape must not stop inference.
                }
            } finally {
                synchronized (clientLifecycleLock) {
                    if (activeClient == client) {
                        activeClient = null;
                    }
                }
            }
        }
    }

    private void serve(Socket client) throws IOException {
        InputStream input = client.getInputStream();
        String requestLine;
        try {
            requestLine = readBoundedLine(input, MAX_REQUEST_LINE_BYTES);
        } catch (LineTooLongException exception) {
            respond(client, 414, "URI Too Long", "request line too long\n");
            return;
        }
        if (requestLine == null) {
            return;
        }

        String[] parts = requestLine.split(" ", 3);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty()
                || !parts[2].startsWith("HTTP/")) {
            respond(client, 400, "Bad Request", "bad request\n");
            return;
        }
        String method = parts[0];
        String path = parts[1];

        int headerBytes = 0;
        int headerCount = 0;
        while (true) {
            String line;
            try {
                line = readBoundedLine(input, MAX_HEADER_LINE_BYTES);
            } catch (LineTooLongException exception) {
                respond(client, 431, "Request Header Fields Too Large", "headers too large\n");
                return;
            }
            if (line == null) {
                respond(client, 400, "Bad Request", "incomplete headers\n");
                return;
            }
            headerBytes += line.length() + 2;
            if (headerBytes > MAX_HEADER_BYTES) {
                respond(client, 431, "Request Header Fields Too Large", "headers too large\n");
                return;
            }
            if (line.isEmpty()) {
                break;
            }
            headerCount++;
            if (headerCount > MAX_HEADER_COUNT) {
                respond(client, 431, "Request Header Fields Too Large", "too many headers\n");
                return;
            }
        }

        if (!"GET".equals(method)) {
            respond(client, 405, "Method Not Allowed", "method not allowed\n");
        } else if (!"/metrics".equals(path)) {
            respond(client, 404, "Not Found", "not found\n");
        } else {
            respond(client, 200, "OK", scrape.get());
        }
    }

    private static String readBoundedLine(InputStream input, int maxBytes)
            throws IOException, LineTooLongException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(Math.min(maxBytes, 256));
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                break;
            }
            if (line.size() >= maxBytes) {
                throw new LineTooLongException();
            }
            line.write(value);
        }
        if (value == -1 && line.size() == 0) {
            return null;
        }
        byte[] bytes = line.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
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

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // The socket is already unusable.
        }
    }

    boolean hasActiveClient() {
        synchronized (clientLifecycleLock) {
            return activeClient != null;
        }
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
        synchronized (clientLifecycleLock) {
            if (activeClient != null) {
                closeQuietly(activeClient);
            }
        }
        try {
            acceptor.join(CLIENT_TIMEOUT_MILLIS + 500L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        closeRegistry.run();
    }

    private static final class LineTooLongException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}

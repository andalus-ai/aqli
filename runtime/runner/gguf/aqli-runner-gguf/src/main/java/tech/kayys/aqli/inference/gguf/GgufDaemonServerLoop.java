package tech.kayys.aqli.inference.gguf;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Owns the accept/idle-timeout loop for the local GGUF daemon.
 *
 * <p>Request decoding and model execution remain with the runtime-provided
 * handler. The server loop deliberately does not know about CLI arguments,
 * sessions, models, or native backends.</p>
 */
public final class GgufDaemonServerLoop {
    private static final int ACCEPT_POLL_MILLIS = 1_000;

    private GgufDaemonServerLoop() {
    }

    @FunctionalInterface
    public interface ClientHandler {
        /** @return true when the daemon should stop accepting requests. */
        boolean handle(Socket socket) throws Exception;
    }

    public static void serve(
            ServerSocket server,
            long idleTimeout,
            TimeUnit idleTimeoutUnit,
            ClientHandler handler,
            Runnable onIdleTimeout,
            Runnable onStopping) throws Exception {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(idleTimeoutUnit, "idleTimeoutUnit");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(onIdleTimeout, "onIdleTimeout");
        Objects.requireNonNull(onStopping, "onStopping");
        if (idleTimeout < 0) {
            throw new IllegalArgumentException("idleTimeout must not be negative");
        }

        long idleNanos = idleTimeoutUnit.toNanos(idleTimeout);
        long lastRequestNanos = System.nanoTime();
        server.setSoTimeout(ACCEPT_POLL_MILLIS);

        boolean stop = false;
        while (!stop) {
            try (Socket socket = server.accept()) {
                lastRequestNanos = System.nanoTime();
                stop = handler.handle(socket);
            } catch (SocketTimeoutException ignored) {
                if (System.nanoTime() - lastRequestNanos > idleNanos) {
                    onIdleTimeout.run();
                    break;
                }
            }
        }
        onStopping.run();
    }
}

package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static tech.kayys.aqli.inference.gguf.GgufDaemonProtocol.readString;

class GgufDaemonControlClientTest {

    private static final String STOP_MAGIC = "AQLI_GGUF_FAST_DAEMON_STOP_V1";

    @Test
    void sendsStopMagicAndReturnsDaemonStatus() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            AtomicReference<Throwable> serverFailure = new AtomicReference<>();
            Thread daemon = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    assertEquals(STOP_MAGIC, readString(in));
                    out.write(0);
                    out.writeInt(7);
                    out.flush();
                } catch (Throwable failure) {
                    serverFailure.set(failure);
                }
            });
            daemon.start();

            int status = new GgufDaemonControlClient().requestStop(
                    server.getLocalPort(), STOP_MAGIC, 1_000, 2);

            daemon.join(2_000);
            assertEquals(7, status);
            if (serverFailure.get() != null) {
                throw new AssertionError("Daemon control server failed", serverFailure.get());
            }
        }
    }

    @Test
    void returnsCommandErrorForNonStatusResponseChannel() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread daemon = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    readString(in);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    out.write(1);
                    out.writeInt(0);
                    out.flush();
                } catch (Exception ignored) {
                }
            });
            daemon.start();

            int status = new GgufDaemonControlClient().requestStop(
                    server.getLocalPort(), STOP_MAGIC, 1_000, 2);

            daemon.join(2_000);
            assertEquals(2, status);
        }
    }

    @Test
    void treatsUnavailableDaemonAsAlreadyStopped() throws Exception {
        int unavailablePort;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            unavailablePort = server.getLocalPort();
        }

        assertEquals(0, new GgufDaemonControlClient().requestStop(
                unavailablePort, STOP_MAGIC, 100, 2));
    }
}

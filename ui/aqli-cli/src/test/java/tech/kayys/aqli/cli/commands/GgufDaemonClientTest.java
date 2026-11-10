package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tech.kayys.aqli.inference.gguf.GgufDaemonProtocol;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GgufDaemonClientTest {

    @Test
    void sendsRequestAndForwardsResponseChannels() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
                try (Socket socket = server.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    assertEquals("AQLI_GGUF_FAST_DAEMON_V1", GgufDaemonProtocol.readString(in));
                    assertEquals(2, in.readInt());
                    assertEquals("--model", GgufDaemonProtocol.readString(in));
                    assertEquals("sample.gguf", GgufDaemonProtocol.readString(in));
                    assertTrue(in.readBoolean());

                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    GgufDaemonProtocol.writeFrame(out, 1, "generated".getBytes(StandardCharsets.UTF_8));
                    GgufDaemonProtocol.writeFrame(out, 2, "diagnostic".getBytes(StandardCharsets.UTF_8));
                    GgufDaemonProtocol.writeStatusFrame(out, 7);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            GgufDaemonClient client = client(server.getLocalPort(), stdout, stderr);
            OptionalInt status = client.send(new String[] {"--model", "sample.gguf"},
                    "AQLI_GGUF_FAST_DAEMON_V1");

            assertTrue(status.isPresent());
            assertEquals(7, status.getAsInt());
            assertEquals("generated", stdout.toString(StandardCharsets.UTF_8));
            assertEquals("diagnostic", stderr.toString(StandardCharsets.UTF_8));
            serverTask.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void returnsEmptyWhenDaemonPortIsUnavailable() {
        GgufDaemonClient client = new GgufDaemonClient(
                OptionalInt::empty,
                () -> 100,
                () -> 100,
                () -> 10L,
                () -> false,
                () -> false,
                () -> false,
                () -> { },
                System.out,
                System.err);

        assertTrue(client.sendWithRetries(new String[0], "magic", 100).isEmpty());
    }

    private static GgufDaemonClient client(
            int port, ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        return new GgufDaemonClient(
                () -> OptionalInt.of(port),
                () -> 1000,
                () -> 2000,
                () -> 10L,
                () -> true,
                () -> false,
                () -> true,
                () -> { },
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }
}

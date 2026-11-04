package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class GgufDaemonServerLoopTest {

    @Test
    void stopsWhenClientHandlerRequestsShutdown() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress())) {
            CountDownLatch handled = new CountDownLatch(1);
            AtomicBoolean idleTimedOut = new AtomicBoolean();
            AtomicBoolean stopping = new AtomicBoolean();

            Thread serving = new Thread(() -> {
                try {
                    GgufDaemonServerLoop.serve(
                            server,
                            30,
                            TimeUnit.SECONDS,
                            socket -> {
                                handled.countDown();
                                return true;
                            },
                            () -> idleTimedOut.set(true),
                            () -> stopping.set(true));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
            serving.start();

            try (Socket ignored = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                assertTrue(handled.await(2, TimeUnit.SECONDS));
            }
            serving.join(2_000);

            assertTrue(!serving.isAlive(), "server loop should stop after handler requests shutdown");
            assertTrue(stopping.get(), "stopping callback should run");
            assertTrue(!idleTimedOut.get(), "idle callback should not run for an explicit stop");
        }
    }
}

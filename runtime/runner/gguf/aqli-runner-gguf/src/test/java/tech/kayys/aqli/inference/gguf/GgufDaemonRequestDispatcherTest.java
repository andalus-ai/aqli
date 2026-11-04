package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class GgufDaemonRequestDispatcherTest {
    @Test
    void stopWritesSuccessStatusAndInvokesStopHandler() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicBoolean stopped = new AtomicBoolean();

        boolean stop = GgufDaemonRequestDispatcher.dispatch(
                request(GgufDaemonRequestDecoder.Kind.STOP),
                new DataOutputStream(bytes),
                42,
                () -> stopped.set(true),
                (request, stdout, stderr) -> 99);

        assertTrue(stop);
        assertTrue(stopped.get());
        assertStatus(bytes.toByteArray(), 0);
    }

    @Test
    void unknownWritesFallbackStatusWithoutInvokingRequestHandler() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicBoolean handled = new AtomicBoolean();

        boolean stop = GgufDaemonRequestDispatcher.dispatch(
                request(GgufDaemonRequestDecoder.Kind.UNKNOWN),
                new DataOutputStream(bytes),
                42,
                () -> { },
                (request, stdout, stderr) -> {
                    handled.set(true);
                    return 99;
                });

        assertFalse(stop);
        assertFalse(handled.get());
        assertStatus(bytes.toByteArray(), 42);
    }

    @Test
    void runFramesStdoutAndStderrBeforeFinalStatus() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        boolean stop = GgufDaemonRequestDispatcher.dispatch(
                request(GgufDaemonRequestDecoder.Kind.RUN),
                new DataOutputStream(bytes),
                42,
                () -> { },
                (request, stdout, stderr) -> {
                    stdout.print("hello");
                    stderr.print("warning");
                    return 7;
                });

        assertFalse(stop);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(1, input.readUnsignedByte());
            assertEquals(5, input.readInt());
            assertEquals("hello", new String(input.readNBytes(5), StandardCharsets.UTF_8));
            assertEquals(2, input.readUnsignedByte());
            assertEquals(7, input.readInt());
            assertEquals("warning", new String(input.readNBytes(7), StandardCharsets.UTF_8));
            assertEquals(0, input.readUnsignedByte());
            assertEquals(7, input.readInt());
            assertEquals(-1, input.read());
        }
    }

    private static GgufDaemonRequestDecoder.Request request(GgufDaemonRequestDecoder.Kind kind) {
        return new GgufDaemonRequestDecoder.Request(kind, new String[0], false);
    }

    private static void assertStatus(byte[] bytes, int expectedStatus) throws Exception {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(0, input.readUnsignedByte());
            assertEquals(expectedStatus, input.readInt());
            assertEquals(-1, input.read());
        }
    }
}

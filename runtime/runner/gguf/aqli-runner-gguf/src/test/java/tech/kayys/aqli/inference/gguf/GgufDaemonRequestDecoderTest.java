package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class GgufDaemonRequestDecoderTest {
    private static final String RUN = "RUN";
    private static final String PREWARM = "PREWARM";
    private static final String STOP = "STOP";

    @Test
    void decodesRunRequestAndTimingFlag() throws Exception {
        var request = decode(out -> {
            GgufDaemonProtocol.writeString(out, RUN);
            out.writeInt(2);
            GgufDaemonProtocol.writeString(out, "--model");
            GgufDaemonProtocol.writeString(out, "model.gguf");
            out.writeBoolean(true);
        });

        assertEquals(GgufDaemonRequestDecoder.Kind.RUN, request.kind());
        assertArrayEquals(new String[] {"--model", "model.gguf"}, request.arguments());
        assertEquals(true, request.requestTiming());
    }

    @Test
    void decodesPrewarmRequest() throws Exception {
        var request = decode(out -> {
            GgufDaemonProtocol.writeString(out, PREWARM);
            out.writeInt(0);
            out.writeBoolean(false);
        });

        assertEquals(GgufDaemonRequestDecoder.Kind.PREWARM, request.kind());
        assertArrayEquals(new String[0], request.arguments());
        assertEquals(false, request.requestTiming());
    }

    @Test
    void stopAndUnknownRequestsDoNotReadAnArgumentPayload() throws Exception {
        var stop = decode(out -> GgufDaemonProtocol.writeString(out, STOP));
        var unknown = decode(out -> GgufDaemonProtocol.writeString(out, "OTHER"));

        assertEquals(GgufDaemonRequestDecoder.Kind.STOP, stop.kind());
        assertEquals(GgufDaemonRequestDecoder.Kind.UNKNOWN, unknown.kind());
    }

    @Test
    void rejectsNegativeAndExcessiveArgumentCounts() throws Exception {
        for (int count : new int[] {-1, GgufDaemonRequestDecoder.MAX_ARGUMENT_COUNT + 1}) {
            assertThrows(IOException.class, () -> decode(out -> {
                GgufDaemonProtocol.writeString(out, RUN);
                out.writeInt(count);
            }));
        }
    }

    private static GgufDaemonRequestDecoder.Request decode(Writer writer) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return GgufDaemonRequestDecoder.read(in, RUN, PREWARM, STOP);
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws Exception;
    }
}

package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class GgufDaemonProtocolTest {

    @Test
    void roundTripsUtf8Strings() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            GgufDaemonProtocol.writeString(out, "Gemma 4 — Jakarta 🌏");
            GgufDaemonProtocol.writeString(out, "");
            GgufDaemonProtocol.writeString(out, null);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals("Gemma 4 — Jakarta 🌏", GgufDaemonProtocol.readString(in));
            assertEquals("", GgufDaemonProtocol.readString(in));
            assertEquals("", GgufDaemonProtocol.readString(in));
        }
    }

    @Test
    void rejectsNegativeAndOversizedStringLengths() throws Exception {
        for (int length : new int[] {-1, GgufDaemonProtocol.MAX_STRING_BYTES + 1}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(length);
            }
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThrows(IOException.class, () -> GgufDaemonProtocol.readString(in));
            }
        }
    }

    @Test
    void rejectsTruncatedStringPayload() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(4);
            out.write("abc".getBytes(StandardCharsets.UTF_8));
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThrows(IOException.class, () -> GgufDaemonProtocol.readString(in));
        }
    }

    @Test
    void writesStatusFrameUsingStatusChannelAndIntegerPayload() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            GgufDaemonProtocol.writeStatusFrame(out, 42);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(0, in.readUnsignedByte());
            assertEquals(42, in.readInt());
            assertEquals(-1, in.read());
        }
    }

    @Test
    void writesChannelFramesAndOmitsEmptyPayloads() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            GgufDaemonProtocol.writeFrame(out, 2, new byte[] {1, 2, 3});
            GgufDaemonProtocol.writeFrame(out, 3, new byte[0]);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(2, in.readUnsignedByte());
            assertEquals(3, in.readInt());
            byte[] payload = in.readNBytes(3);
            assertArrayEquals(new byte[] {1, 2, 3}, payload);
            assertEquals(-1, in.read());
        }
    }
}

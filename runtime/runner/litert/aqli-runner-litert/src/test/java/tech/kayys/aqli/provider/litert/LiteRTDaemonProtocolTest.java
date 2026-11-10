package tech.kayys.aqli.provider.litert;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LiteRTDaemonProtocolTest {

    @Test
    void roundTripsUtf8StringsAndNullAsEmpty() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        LiteRTDaemonProtocol.writeString(out, "Gemma 日本語");
        LiteRTDaemonProtocol.writeString(out, null);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("Gemma 日本語", LiteRTDaemonProtocol.readString(in));
        assertEquals("", LiteRTDaemonProtocol.readString(in));
    }

    @Test
    void rejectsNegativeAndOversizedStringLengths() throws Exception {
        for (int length : new int[] {-1, LiteRTDaemonProtocol.MAX_STRING_BYTES + 1}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new DataOutputStream(bytes).writeInt(length);
            assertThrows(IOException.class, () -> LiteRTDaemonProtocol.readString(
                    new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
    }

    @Test
    void rejectsTruncatedStringPayload() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(4);
        out.write(new byte[] {1, 2});

        assertThrows(IOException.class, () -> LiteRTDaemonProtocol.readString(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void writesOutputFramesAndTerminalStatusUsingStableChannels() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try (var stdout = new LiteRTDaemonProtocol.FramedOutputStream(out, 1);
             var stderr = new LiteRTDaemonProtocol.FramedOutputStream(out, 2)) {
            stdout.write("hello\n".getBytes(StandardCharsets.UTF_8));
            stderr.write("warning".getBytes(StandardCharsets.UTF_8));
        }
        LiteRTDaemonProtocol.writeStatus(out, 42);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1, in.readUnsignedByte());
        assertEquals(6, in.readInt());
        assertEquals("hello\n", new String(in.readNBytes(6), StandardCharsets.UTF_8));
        assertEquals(2, in.readUnsignedByte());
        assertEquals(7, in.readInt());
        assertEquals("warning", new String(in.readNBytes(7), StandardCharsets.UTF_8));
        assertEquals(0, in.readUnsignedByte());
        assertEquals(42, in.readInt());
        assertEquals(0, in.available());
    }

    @Test
    void rejectsInvalidFrameChannels() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        assertThrows(IllegalArgumentException.class, () -> new LiteRTDaemonProtocol.FramedOutputStream(out, 0));
        assertThrows(IllegalArgumentException.class, () -> LiteRTDaemonProtocol.writeFrame(out, 256, new byte[0]));
    }
}

package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GgufDaemonFramedOutputStreamTest {

    @Test
    void buffersWritesUntilFlushAndFramesPayloadOnConfiguredChannel() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream target = new DataOutputStream(bytes)) {
            GgufDaemonFramedOutputStream stream = new GgufDaemonFramedOutputStream(target, 2);
            stream.write("err".getBytes(StandardCharsets.UTF_8));
            assertEquals(0, bytes.size());

            stream.flush();
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertEquals(2, in.readUnsignedByte());
                int length = in.readInt();
                assertEquals(3, length);
                assertEquals("err", new String(in.readNBytes(length), StandardCharsets.UTF_8));
                assertEquals(-1, in.read());
            }
        }
    }

    @Test
    void supportsSingleByteAndOffsetWrites() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream target = new DataOutputStream(bytes);
        GgufDaemonFramedOutputStream stream = new GgufDaemonFramedOutputStream(target, 1);
        stream.write('A');
        stream.write(new byte[] {'x', 'B', 'y'}, 1, 1);
        stream.flush();

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(1, in.readUnsignedByte());
            assertEquals(2, in.readInt());
            assertEquals("AB", new String(in.readNBytes(2), StandardCharsets.UTF_8));
        }
    }

    @Test
    void emptyFlushDoesNotEmitAFrame() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GgufDaemonFramedOutputStream stream =
                new GgufDaemonFramedOutputStream(new DataOutputStream(bytes), 1);

        stream.flush();

        assertEquals(0, bytes.size());
    }
}

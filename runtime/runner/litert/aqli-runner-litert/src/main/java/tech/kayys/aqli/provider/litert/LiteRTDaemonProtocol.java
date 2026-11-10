package tech.kayys.aqli.provider.litert;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Wire-format primitives shared by the LiteRT-LM standalone client and daemon.
 *
 * <p>This class deliberately contains no socket creation, process launching,
 * daemon discovery, or CLI policy. It preserves the existing versioned wire
 * format so client and daemon can be refactored independently.</p>
 */
public final class LiteRTDaemonProtocol {
    public static final String RUN_MAGIC = "AQLI_LITERT_FAST_DAEMON_V1";
    public static final String PREWARM_MAGIC = "AQLI_LITERT_FAST_DAEMON_PREWARM_V1";
    public static final String STOP_MAGIC = "AQLI_LITERT_FAST_DAEMON_STOP_V1";
    public static final int MAX_STRING_BYTES = 16 * 1024 * 1024;

    private LiteRTDaemonProtocol() {
    }

    public static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("Invalid LiteRT-LM daemon string length: " + length);
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Unexpected end of LiteRT-LM daemon string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeStatus(DataOutputStream out, int status) throws IOException {
        synchronized (out) {
            out.writeByte(0);
            out.writeInt(status);
            out.flush();
        }
    }

    public static void writeFrame(DataOutputStream out, int channel, byte[] payload) throws IOException {
        if (channel <= 0 || channel > 255) {
            throw new IllegalArgumentException("Frame channel must be between 1 and 255: " + channel);
        }
        if (payload == null) {
            throw new NullPointerException("payload");
        }
        synchronized (out) {
            out.writeByte(channel);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        }
    }

    public static final class FramedOutputStream extends OutputStream {
        private final DataOutputStream out;
        private final int channel;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);

        public FramedOutputStream(DataOutputStream out, int channel) {
            if (channel <= 0 || channel > 255) {
                throw new IllegalArgumentException("Frame channel must be between 1 and 255: " + channel);
            }
            this.out = out;
            this.channel = channel;
        }

        @Override
        public void write(int value) throws IOException {
            buffer.write(value);
            if (value == '\n' || buffer.size() >= 4096) {
                flush();
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length <= 0) {
                return;
            }
            if (buffer.size() == 0 && length >= 4096) {
                byte[] copy = new byte[length];
                System.arraycopy(bytes, offset, copy, 0, length);
                writeFrame(out, channel, copy);
                return;
            }
            buffer.write(bytes, offset, length);
            if (buffer.size() >= 4096 || bytes[offset + length - 1] == '\n') {
                flush();
            }
        }

        @Override
        public void flush() throws IOException {
            if (buffer.size() == 0) {
                return;
            }
            byte[] payload = buffer.toByteArray();
            buffer.reset();
            writeFrame(out, channel, payload);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}

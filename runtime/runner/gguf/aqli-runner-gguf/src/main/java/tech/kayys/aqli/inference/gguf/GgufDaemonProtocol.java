package tech.kayys.aqli.inference.gguf;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Wire-format primitives shared by the AQLI GGUF daemon and its clients.
 *
 * <p>This class owns framing only. It deliberately has no knowledge of CLI
 * arguments, model execution, sessions, or native backends.</p>
 */
public final class GgufDaemonProtocol {
    public static final int MAX_STRING_BYTES = 1_048_576;
    private static final int STATUS_CHANNEL = 0;

    private GgufDaemonProtocol() {
    }

    public static void writeStatusFrame(DataOutputStream out, int status) throws IOException {
        Objects.requireNonNull(out, "out");
        synchronized (out) {
            out.write(STATUS_CHANNEL);
            out.writeInt(status);
            out.flush();
        }
    }

    public static void writeFrame(DataOutputStream out, int channel, byte[] payload) throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0) {
            return;
        }
        synchronized (out) {
            out.write(channel);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        }
    }

    public static void writeString(DataOutputStream out, String value) throws IOException {
        Objects.requireNonNull(out, "out");
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public static String readString(DataInputStream in) throws IOException {
        Objects.requireNonNull(in, "in");
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("Invalid GGUF daemon string length: " + length);
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Unexpected end of GGUF daemon string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

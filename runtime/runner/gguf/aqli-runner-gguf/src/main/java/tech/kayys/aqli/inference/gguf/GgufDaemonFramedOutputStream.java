package tech.kayys.aqli.inference.gguf;

import static tech.kayys.aqli.inference.gguf.GgufDaemonProtocol.writeFrame;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Buffers text output and emits it as one GGUF daemon channel frame on flush.
 */
public final class GgufDaemonFramedOutputStream extends OutputStream {
    private final DataOutputStream target;
    private final int channel;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public GgufDaemonFramedOutputStream(DataOutputStream target, int channel) {
        this.target = Objects.requireNonNull(target, "target");
        this.channel = channel;
    }

    @Override
    public void write(int value) {
        buffer.write(value);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        buffer.write(bytes, offset, length);
    }

    @Override
    public void flush() throws IOException {
        byte[] payload = buffer.toByteArray();
        buffer.reset();
        writeFrame(target, channel, payload);
    }
}

package tech.kayys.aqli.inference.gguf;

import static tech.kayys.aqli.inference.gguf.GgufDaemonProtocol.readString;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

/** Decodes the request envelope exchanged with the local GGUF daemon. */
public final class GgufDaemonRequestDecoder {
    public static final int MAX_ARGUMENT_COUNT = 512;

    public enum Kind {
        STOP,
        RUN,
        PREWARM,
        UNKNOWN
    }

    public record Request(Kind kind, String[] arguments, boolean requestTiming) {
        public Request {
            Objects.requireNonNull(kind, "kind");
            arguments = arguments == null ? new String[0] : arguments.clone();
        }

        @Override
        public String[] arguments() {
            return arguments.clone();
        }
    }

    private GgufDaemonRequestDecoder() {
    }

    public static Request read(
            DataInputStream in,
            String runMagic,
            String prewarmMagic,
            String stopMagic) throws IOException {
        Objects.requireNonNull(in, "in");
        String magic = readString(in);
        if (stopMagic.equals(magic)) {
            return new Request(Kind.STOP, new String[0], false);
        }
        Kind kind;
        if (runMagic.equals(magic)) {
            kind = Kind.RUN;
        } else if (prewarmMagic.equals(magic)) {
            kind = Kind.PREWARM;
        } else {
            return new Request(Kind.UNKNOWN, new String[0], false);
        }

        int argumentCount = in.readInt();
        if (argumentCount < 0 || argumentCount > MAX_ARGUMENT_COUNT) {
            throw new IOException("Invalid GGUF daemon arg count: " + argumentCount);
        }
        String[] arguments = new String[argumentCount];
        for (int i = 0; i < argumentCount; i++) {
            arguments[i] = readString(in);
        }
        return new Request(kind, arguments, in.readBoolean());
    }
}

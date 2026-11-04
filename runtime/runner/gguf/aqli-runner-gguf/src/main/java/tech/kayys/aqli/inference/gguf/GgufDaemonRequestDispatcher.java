package tech.kayys.aqli.inference.gguf;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Dispatches decoded GGUF daemon requests and writes their framed response.
 *
 * <p>Execution policy remains supplied by the runtime owner. This class only
 * coordinates protocol-level STOP/UNKNOWN handling and RUN/PREWARM output
 * framing.</p>
 */
public final class GgufDaemonRequestDispatcher {
    @FunctionalInterface
    public interface RequestHandler {
        int handle(
                GgufDaemonRequestDecoder.Request request,
                PrintStream stdout,
                PrintStream stderr) throws Exception;
    }

    private GgufDaemonRequestDispatcher() {
    }

    /**
     * Dispatches one decoded request. Returns {@code true} only for STOP.
     */
    public static boolean dispatch(
            GgufDaemonRequestDecoder.Request request,
            DataOutputStream framed,
            int fallbackStatus,
            Runnable stopHandler,
            RequestHandler requestHandler) throws Exception {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(framed, "framed");
        Objects.requireNonNull(stopHandler, "stopHandler");
        Objects.requireNonNull(requestHandler, "requestHandler");

        if (request.kind() == GgufDaemonRequestDecoder.Kind.STOP) {
            GgufDaemonProtocol.writeStatusFrame(framed, 0);
            stopHandler.run();
            return true;
        }
        if (request.kind() == GgufDaemonRequestDecoder.Kind.UNKNOWN) {
            GgufDaemonProtocol.writeStatusFrame(framed, fallbackStatus);
            return false;
        }

        PrintStream stdout = new PrintStream(
                new GgufDaemonFramedOutputStream(framed, 1), true, StandardCharsets.UTF_8);
        PrintStream stderr = new PrintStream(
                new GgufDaemonFramedOutputStream(framed, 2), true, StandardCharsets.UTF_8);
        int status = requestHandler.handle(request, stdout, stderr);
        stdout.flush();
        stderr.flush();
        GgufDaemonProtocol.writeStatusFrame(framed, status);
        return false;
    }
}

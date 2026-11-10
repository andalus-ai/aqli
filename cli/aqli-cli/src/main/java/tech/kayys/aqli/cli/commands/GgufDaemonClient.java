package tech.kayys.aqli.cli.commands;

import static tech.kayys.aqli.inference.gguf.GgufDaemonProtocol.writeString;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Client-side transport for the local GGUF daemon protocol.
 *
 * <p>Owns socket connection, request framing, response-frame forwarding and
 * retry timing. Daemon discovery, startup and lifecycle remain with the runner.
 */
final class GgufDaemonClient {
    private final Supplier<OptionalInt> portProvider;
    private final IntSupplier connectTimeoutMillis;
    private final IntSupplier socketTimeoutMillis;
    private final LongSupplier retrySleepMillis;
    private final BooleanSupplier requestTiming;
    private final BooleanSupplier debugEnabled;
    private final BooleanSupplier daemonInfoPresent;
    private final Runnable stalePortCleanup;
    private final PrintStream stdout;
    private final PrintStream stderr;

    GgufDaemonClient(
            Supplier<OptionalInt> portProvider,
            IntSupplier connectTimeoutMillis,
            IntSupplier socketTimeoutMillis,
            LongSupplier retrySleepMillis,
            BooleanSupplier requestTiming,
            BooleanSupplier debugEnabled,
            BooleanSupplier daemonInfoPresent,
            Runnable stalePortCleanup,
            PrintStream stdout,
            PrintStream stderr) {
        this.portProvider = portProvider;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.socketTimeoutMillis = socketTimeoutMillis;
        this.retrySleepMillis = retrySleepMillis;
        this.requestTiming = requestTiming;
        this.debugEnabled = debugEnabled;
        this.daemonInfoPresent = daemonInfoPresent;
        this.stalePortCleanup = stalePortCleanup;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    OptionalInt sendWithRetries(String[] rawArgs, String magic, long retryMillis) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, retryMillis));
        while (true) {
            OptionalInt status = send(rawArgs, magic);
            if (status.isPresent()) {
                return status;
            }
            if (!daemonInfoPresent.getAsBoolean() || System.nanoTime() >= deadlineNanos) {
                return OptionalInt.empty();
            }
            try {
                Thread.sleep(retrySleepMillis.getAsLong());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return OptionalInt.empty();
            }
        }
    }

    OptionalInt send(String[] rawArgs, String magic) {
        OptionalInt port = portProvider.get();
        if (port.isEmpty()) {
            return OptionalInt.empty();
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port.getAsInt()),
                    connectTimeoutMillis.getAsInt());
            socket.setSoTimeout(socketTimeoutMillis.getAsInt());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            writeString(out, magic);
            out.writeInt(rawArgs.length);
            for (String arg : rawArgs) {
                writeString(out, arg);
            }
            out.writeBoolean(requestTiming.getAsBoolean());
            out.flush();
            while (true) {
                int channel = in.read();
                if (channel < 0) {
                    return OptionalInt.empty();
                }
                int length = in.readInt();
                if (length < 0) {
                    return OptionalInt.empty();
                }
                if (channel == 0) {
                    return OptionalInt.of(length);
                }
                byte[] payload = in.readNBytes(length);
                if (payload.length != length) {
                    return OptionalInt.empty();
                }
                PrintStream target = channel == 2 ? stderr : stdout;
                target.write(payload);
                target.flush();
            }
        } catch (ConnectException ignored) {
            stalePortCleanup.run();
            return OptionalInt.empty();
        } catch (Exception e) {
            if (debugEnabled.getAsBoolean()) {
                stderr.println("GGUF daemon request failed: " + briefMessage(e));
            }
            return OptionalInt.empty();
        }
    }

    private static String briefMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            return cursor.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}

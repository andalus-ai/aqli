package tech.kayys.aqli.cli.commands;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Coordinates detached daemon launch and readiness detection.
 *
 * <p>Configuration, daemon command construction, stale-port cleanup, and
 * failure reporting remain owned by the runner. This component coordinates
 * the launcher with the daemon-port readiness probe.</p>
 */
final class GgufDaemonStartupCoordinator {

    @FunctionalInterface
    interface Launcher {
        boolean launch(
                List<String> command,
                String mode,
                String label,
                Path logFile,
                boolean macOs,
                boolean debug,
                String nativePaths) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Launcher launcher;
    private final Supplier<OptionalInt> portProvider;
    private final LongSupplier startupTimeoutMillis;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private final long readinessPollMillis;
    private final Supplier<java.util.Optional<String>> startupFailure;
    private final Consumer<String> failureReporter;

    GgufDaemonStartupCoordinator(
            Launcher launcher,
            Supplier<OptionalInt> portProvider,
            LongSupplier startupTimeoutMillis,
            LongSupplier nanoTime,
            Sleeper sleeper,
            long readinessPollMillis) {
        this(launcher, portProvider, startupTimeoutMillis, nanoTime, sleeper,
                readinessPollMillis, java.util.Optional::empty, ignored -> { });
    }

    GgufDaemonStartupCoordinator(
            Launcher launcher,
            Supplier<OptionalInt> portProvider,
            LongSupplier startupTimeoutMillis,
            LongSupplier nanoTime,
            Sleeper sleeper,
            long readinessPollMillis,
            Supplier<java.util.Optional<String>> startupFailure,
            Consumer<String> failureReporter) {
        this.launcher = launcher;
        this.portProvider = portProvider;
        this.startupTimeoutMillis = startupTimeoutMillis;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
        this.readinessPollMillis = Math.max(1L, readinessPollMillis);
        this.startupFailure = startupFailure;
        this.failureReporter = failureReporter;
    }

    boolean start(
            List<String> command,
            String mode,
            String label,
            Path logFile,
            boolean macOs,
            boolean debug,
            String nativePaths) throws IOException, InterruptedException {
        long logOffset = logSize(logFile);
        if (!launcher.launch(command, mode, label, logFile, macOs, debug, nativePaths)) {
            report("GGUF daemon launcher did not accept the startup request; see " + logFile);
            return false;
        }

        long timeoutMillis = Math.max(0L, startupTimeoutMillis.getAsLong());
        long deadlineNanos = nanoTime.getAsLong() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (nanoTime.getAsLong() < deadlineNanos) {
            if (portProvider.get().isPresent()) {
                return true;
            }
            java.util.Optional<String> failure = startupFailure.get();
            if (failure.isPresent()) {
                reportFailure("daemon process failed during startup: " + failure.get());
                return false;
            }
            sleeper.sleep(readinessPollMillis);
        }
        if (portProvider.get().isPresent()) {
            return true;
        }
        OptionalFailure failure = startupFailure(logFile, logOffset);
        if (failure.message != null) {
            report("GGUF daemon exited during startup: " + failure.message
                    + " (log: " + logFile + ")");
        } else {
            report("GGUF daemon did not become ready within " + timeoutMillis
                    + " ms; inspect " + logFile);
        }
        return false;
    }

    private void report(String message) {
        try {
            startupFailureReporter.accept(message);
        } catch (RuntimeException ignored) {
            // Diagnostics must not change daemon startup or readiness behavior.
        }
    }

    private static long logSize(Path logFile) {
        try {
            return Files.exists(logFile) ? Files.size(logFile) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static OptionalFailure startupFailure(Path logFile, long logOffset) {
        if (!Files.isRegularFile(logFile)) {
            return new OptionalFailure(null);
        }
        try (SeekableByteChannel channel = Files.newByteChannel(logFile, StandardOpenOption.READ)) {
            long size = channel.size();
            channel.position(Math.min(logOffset, size));
            ByteBuffer buffer = ByteBuffer.allocate(65_536);
            StringBuilder appended = new StringBuilder();
            while (appended.length() < 65_536 && channel.read(buffer) > 0) {
                buffer.flip();
                appended.append(StandardCharsets.UTF_8.decode(buffer));
                buffer.clear();
            }
            String marker = "GGUF daemon failed:";
            int markerIndex = appended.indexOf(marker);
            if (markerIndex < 0) {
                return new OptionalFailure(null);
            }
            int lineEnd = appended.indexOf("\n", markerIndex);
            String line = appended.substring(markerIndex, lineEnd < 0 ? appended.length() : lineEnd).trim();
            return new OptionalFailure(line);
        } catch (IOException ignored) {
            return new OptionalFailure(null);
        }
    }

    private record OptionalFailure(String message) {
        reportFailure("daemon did not publish its port within " + timeoutMillis
                + " ms; inspect the daemon log for details");
        return false;
    }

    private void reportFailure(String message) {
        try {
            failureReporter.accept(message);
        } catch (RuntimeException ignored) {
            // Diagnostics must not change startup outcome.
        }
    }
}

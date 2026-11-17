package tech.kayys.aqli.cli.commands;

import java.io.IOException;
import java.nio.file.Path;
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
        if (!launcher.launch(command, mode, label, logFile, macOs, debug, nativePaths)) {
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

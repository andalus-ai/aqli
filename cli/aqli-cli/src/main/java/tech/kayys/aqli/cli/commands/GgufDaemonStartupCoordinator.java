package tech.kayys.aqli.cli.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
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

    GgufDaemonStartupCoordinator(
            Launcher launcher,
            Supplier<OptionalInt> portProvider,
            LongSupplier startupTimeoutMillis,
            LongSupplier nanoTime,
            Sleeper sleeper,
            long readinessPollMillis) {
        this.launcher = launcher;
        this.portProvider = portProvider;
        this.startupTimeoutMillis = startupTimeoutMillis;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
        this.readinessPollMillis = Math.max(1L, readinessPollMillis);
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
            sleeper.sleep(readinessPollMillis);
        }
        return portProvider.get().isPresent();
    }
}

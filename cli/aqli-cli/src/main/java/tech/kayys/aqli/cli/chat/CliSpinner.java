package tech.kayys.aqli.cli.chat;

import java.io.PrintStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Animated spinner shown while waiting for an inference response.
 * Clears itself from the line before the assistant output begins.
 */
public class CliSpinner {

    private static final String[] FRAMES = { "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏" };
    private static final String HIDE_CURSOR = "\u001B[?25l";
    private static final String SHOW_CURSOR = "\u001B[?25h";
    private static final String CLEAR_LINE  = "\r\u001B[2K";

    private final PrintStream out;
    private final String label;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private int frameIndex = 0;

    public CliSpinner(PrintStream out, String label) {
        this.out = out;
        this.label = label;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        out.print(HIDE_CURSOR);
        out.flush();
        long startMs = System.currentTimeMillis();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cli-spinner");
            t.setDaemon(true);
            return t;
        });
        task = scheduler.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startMs) / 1000;
            String elapsedStr = elapsed > 0 ? " (" + elapsed + "s)" : "";
            out.print(CLEAR_LINE + ChatUIRenderer.DIM + FRAMES[frameIndex % FRAMES.length]
                    + " " + label + elapsedStr + ChatUIRenderer.RESET);
            out.flush();
            frameIndex++;
        }, 0, 80, TimeUnit.MILLISECONDS);
    }

    /** Stop the spinner and erase it from the terminal line. */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        out.print(CLEAR_LINE + SHOW_CURSOR);
        out.flush();
    }
}

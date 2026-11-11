package tech.kayys.aqli.cli.commands.progress;

import tech.kayys.aljabr.spi.download.DownloadProgressListener;

/**
 * Renders repository download progress in the interactive CLI.
 *
 * <p>This is terminal presentation only; download orchestration and progress
 * reporting remain owned by the repository implementation.</p>
 */
public final class TerminalDownloadProgressRenderer implements DownloadProgressListener, AutoCloseable {
    private static final String[] SPINNER = { "|", "/", "-", "\\" };
    private static final int BAR_WIDTH = 30;
    private static final long MIN_REDRAW_NS = 80_000_000L;

    private final int totalFiles;
    private int completedFiles;
    private int spinnerTick;
    private long fileStartNanos = System.nanoTime();
    private long lastRedrawNanos;

    public TerminalDownloadProgressRenderer(int totalFiles) {
        this.totalFiles = Math.max(0, totalFiles);
    }

    @Override
    public synchronized void onProgress(long downloadedBytes, long totalBytes, double progress) {
        long now = System.nanoTime();
        if (now - lastRedrawNanos < MIN_REDRAW_NS && downloadedBytes < totalBytes) {
            return;
        }
        lastRedrawNanos = now;

        double pct = Math.max(0.0, Math.min(1.0, progress));
        int filled = Math.min(BAR_WIDTH, (int) Math.round(BAR_WIDTH * pct));
        StringBuilder bar = new StringBuilder(BAR_WIDTH);
        for (int i = 0; i < BAR_WIDTH; i++) {
            bar.append(i < filled ? "=" : ".");
        }

        double elapsedSec = Math.max(0.001, (now - fileStartNanos) / 1_000_000_000.0);
        double mbDone = downloadedBytes / 1024.0 / 1024.0;
        double mbTotal = totalBytes > 0 ? totalBytes / 1024.0 / 1024.0 : 0.0;
        double speed = mbDone / elapsedSec;
        String spin = SPINNER[spinnerTick++ % SPINNER.length];

        String fileProgress = totalFiles > 0
                ? (Math.min(completedFiles + 1, totalFiles) + "/" + totalFiles)
                : Integer.toString(completedFiles + 1);
        System.out.printf("\r%s [%-30s] %3d%% %.1f/%.1f MB %.1f MB/s file %s",
                spin,
                bar,
                (int) Math.round(pct * 100.0),
                mbDone,
                mbTotal,
                speed,
                fileProgress);
        System.out.flush();
    }

    @Override
    public synchronized void onComplete(long totalBytes) {
        completedFiles++;
        fileStartNanos = System.nanoTime();
        String fileProgress = totalFiles > 0
                ? (Math.min(completedFiles, totalFiles) + "/" + totalFiles)
                : Integer.toString(completedFiles);
        System.out.printf("\r* [%-30s] 100%% file %s done%n",
                "==============================",
                fileProgress);
        System.out.flush();
    }

    @Override
    public void close() {
        System.out.flush();
    }
}

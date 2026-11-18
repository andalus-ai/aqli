package tech.kayys.aqli.cli.commands;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes daemon startup across CLI threads and processes.
 *
 * <p>The JVM lock prevents overlapping file-lock attempts from threads in
 * this process; the operating-system file lock coordinates separate CLI
 * processes.</p>
 */
final class GgufDaemonStartupLock implements AutoCloseable {
    private static final ReentrantLock JVM_LOCK = new ReentrantLock();

    private final FileChannel channel;
    private final FileLock fileLock;

    private GgufDaemonStartupLock(FileChannel channel, FileLock fileLock) {
        this.channel = channel;
        this.fileLock = fileLock;
    }

    static GgufDaemonStartupLock acquire(Path lockFile) throws IOException, InterruptedException {
        Objects.requireNonNull(lockFile, "lockFile");
        JVM_LOCK.lockInterruptibly();
        FileChannel channel = null;
        try {
            Path parent = lockFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            FileLock lock = channel.lock();
            return new GgufDaemonStartupLock(channel, lock);
        } catch (IOException | RuntimeException exception) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            JVM_LOCK.unlock();
            throw exception;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            fileLock.release();
        } finally {
            try {
                channel.close();
            } finally {
                JVM_LOCK.unlock();
            }
        }
    }
}

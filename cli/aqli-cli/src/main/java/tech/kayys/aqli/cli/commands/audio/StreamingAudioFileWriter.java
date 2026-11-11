package tech.kayys.aqli.cli.commands.audio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Writes streamed audio to a temporary file and atomically publishes the completed artifact. */
public final class StreamingAudioFileWriter implements AutoCloseable {
    private final Path finalPath;
    private Path temporaryPath;
    private OutputStream output;

    public StreamingAudioFileWriter(Path finalPath) {
        this.finalPath = finalPath.toAbsolutePath().normalize();
    }

    public void open() throws IOException {
        if (output != null) {
            return;
        }
        Path parent = finalPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporaryDirectory = parent == null ? Path.of(".") : parent;
        String name = finalPath.getFileName() == null ? "aqli-audio" : finalPath.getFileName().toString();
        temporaryPath = Files.createTempFile(temporaryDirectory, "." + name + ".", ".part");
        output = Files.newOutputStream(
                temporaryPath,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void write(byte[] bytes) throws IOException {
        if (output == null) {
            throw new IllegalStateException("streaming audio writer is not open");
        }
        if (bytes != null && bytes.length > 0) {
            output.write(bytes);
        }
    }

    public Path finish() throws IOException {
        if (temporaryPath == null) {
            throw new IllegalStateException("streaming audio writer has no pending output");
        }
        close();
        try {
            Files.move(temporaryPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
        }
        temporaryPath = null;
        return finalPath;
    }

    public void abort() throws IOException {
        close();
        if (temporaryPath != null) {
            Files.deleteIfExists(temporaryPath);
            temporaryPath = null;
        }
    }

    @Override
    public void close() throws IOException {
        if (output != null) {
            output.close();
            output = null;
        }
    }
}

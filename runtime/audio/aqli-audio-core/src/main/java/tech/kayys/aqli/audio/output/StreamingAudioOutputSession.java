package tech.kayys.aqli.audio.output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Accumulates encoded audio chunks and switches to an atomic file writer when
 * the caller chooses direct-to-file output.
 *
 * <p>The session owns stream buffering and temporary-file lifecycle only.
 * Output-path selection, metadata interpretation, sidecars, and presentation
 * remain with the calling adapter.</p>
 */
public final class StreamingAudioOutputSession implements AutoCloseable {
    private final ByteArrayOutputStream memory = new ByteArrayOutputStream();

    private StreamingAudioFileWriter fileWriter;
    private long bytesWritten;
    private boolean finished;

    public void write(byte[] chunk, boolean streamDirectlyToFile, Path targetPath) throws IOException {
        ensureOpen();
        if (chunk == null || chunk.length == 0) {
            return;
        }

        if (streamDirectlyToFile) {
            ensureFileWriter(targetPath);
            fileWriter.write(chunk);
        } else if (fileWriter == null) {
            memory.write(chunk);
        } else {
            fileWriter.write(chunk);
        }
        bytesWritten += chunk.length;
    }

    public boolean hasOutput() {
        return bytesWritten > 0;
    }

    /**
     * Completes the session and returns either buffered bytes or the published file.
     */
    public Output finish() throws IOException {
        ensureOpen();
        if (fileWriter == null) {
            finished = true;
            return new BufferedOutput(memory.toByteArray());
        }

        try {
            return new FileOutput(fileWriter.finish());
        } catch (IOException | RuntimeException failure) {
            try {
                fileWriter.abort();
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        } finally {
            fileWriter = null;
            finished = true;
        }
    }

    private void ensureFileWriter(Path targetPath) throws IOException {
        if (fileWriter != null) {
            return;
        }
        if (targetPath == null) {
            throw new IllegalArgumentException("A target path is required for direct-to-file audio output");
        }
        fileWriter = new StreamingAudioFileWriter(targetPath);
        fileWriter.open();
        if (memory.size() > 0) {
            fileWriter.write(memory.toByteArray());
            memory.reset();
        }
    }

    private void ensureOpen() {
        if (finished) {
            throw new IllegalStateException("audio output session is already finished");
        }
    }

    @Override
    public void close() throws IOException {
        if (finished) {
            return;
        }
        finished = true;
        if (fileWriter != null) {
            try {
                fileWriter.abort();
            } finally {
                fileWriter = null;
            }
        }
    }

    public sealed interface Output permits BufferedOutput, FileOutput {
    }

    public record BufferedOutput(byte[] bytes) implements Output {
        public BufferedOutput {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record FileOutput(Path path) implements Output {
        public FileOutput {
            path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        }
    }
}

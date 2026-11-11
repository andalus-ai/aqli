package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamingAudioFileWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesChunksAndPublishesOnlyWhenFinished() throws Exception {
        Path output = tempDir.resolve("nested/audio.wav");
        StreamingAudioFileWriter writer = new StreamingAudioFileWriter(output);
        writer.open();
        writer.write(new byte[] {1, 2});
        writer.write(new byte[] {3, 4});

        assertFalse(Files.exists(output));
        assertTrue(Files.list(output.getParent()).anyMatch(path -> path.getFileName().toString().endsWith(".part")));

        assertTrue(writer.finish().equals(output.toAbsolutePath().normalize()));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(output));
        assertFalse(Files.list(output.getParent()).anyMatch(path -> path.getFileName().toString().endsWith(".part")));
    }

    @Test
    void abortRemovesTemporaryFileWithoutPublishingOutput() throws Exception {
        Path output = tempDir.resolve("audio.wav");
        StreamingAudioFileWriter writer = new StreamingAudioFileWriter(output);
        writer.open();
        writer.write(new byte[] {9, 8});

        writer.abort();

        assertFalse(Files.exists(output));
        try (var files = Files.list(tempDir)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void rejectsWritesBeforeOpening() throws Exception {
        StreamingAudioFileWriter writer = new StreamingAudioFileWriter(tempDir.resolve("audio.wav"));
        assertThrows(IllegalStateException.class, () -> writer.write(new byte[] {1}));
    }
}

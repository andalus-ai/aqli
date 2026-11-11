package tech.kayys.aqli.audio.output;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamingAudioOutputSessionTest {
    @TempDir
    Path tempDir;

    @Test
    void buffersChunksWhenDirectFileOutputIsNotRequested() throws Exception {
        StreamingAudioOutputSession session = new StreamingAudioOutputSession();
        session.write(new byte[] {1, 2}, false, null);
        session.write(new byte[] {3, 4}, false, null);

        assertTrue(session.hasOutput());
        StreamingAudioOutputSession.BufferedOutput output =
                assertInstanceOf(StreamingAudioOutputSession.BufferedOutput.class, session.finish());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, output.bytes());
    }

    @Test
    void streamsChunksToAtomicFileWhenRequested() throws Exception {
        Path target = tempDir.resolve("nested/audio.wav");
        StreamingAudioOutputSession session = new StreamingAudioOutputSession();
        session.write(new byte[] {1, 2}, true, target);
        session.write(new byte[] {3, 4}, true, target);

        assertFalse(Files.exists(target));
        StreamingAudioOutputSession.FileOutput output =
                assertInstanceOf(StreamingAudioOutputSession.FileOutput.class, session.finish());
        assertEquals(target.toAbsolutePath().normalize(), output.path());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(target));
    }

    @Test
    void migratesPreviouslyBufferedChunksWhenDirectFileOutputBegins() throws Exception {
        Path target = tempDir.resolve("audio.wav");
        StreamingAudioOutputSession session = new StreamingAudioOutputSession();
        session.write(new byte[] {1, 2}, false, null);
        session.write(new byte[] {3, 4}, true, target);

        StreamingAudioOutputSession.FileOutput output =
                assertInstanceOf(StreamingAudioOutputSession.FileOutput.class, session.finish());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(output.path()));
    }

    @Test
    void closeAbortsTemporaryFileWithoutPublishingOutput() throws Exception {
        Path target = tempDir.resolve("audio.wav");
        StreamingAudioOutputSession session = new StreamingAudioOutputSession();
        session.write(new byte[] {1, 2}, true, target);

        session.close();

        assertFalse(Files.exists(target));
        try (var files = Files.list(tempDir)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void rejectsDirectFileOutputWithoutTargetPath() {
        StreamingAudioOutputSession session = new StreamingAudioOutputSession();
        assertThrows(IllegalArgumentException.class, () -> session.write(new byte[] {1}, true, null));
    }

    @Test
    void rejectsWritesAfterFinish() throws Exception {
        StreamingAudioOutputSession session = new StreamingAudioOutputSession();
        session.finish();
        assertThrows(IllegalStateException.class, () -> session.write(new byte[] {1}, false, null));
    }
}

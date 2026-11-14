package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaOutputCliHandlerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesImageAndInvokesOnlyImageSidecarWriter() throws Exception {
        Path output = temporaryDirectory.resolve("generated.png");
        AtomicInteger imageWrites = new AtomicInteger();
        AtomicInteger audioWrites = new AtomicInteger();
        Map<String, Object> metadata = Map.of("pipeline", "image");

        MediaOutputCliHandler handler = new MediaOutputCliHandler(
                output.toString(),
                null,
                ignored -> "flac",
                (path, values) -> audioWrites.incrementAndGet(),
                (path, values) -> {
                    assertEquals(output, path);
                    assertEquals(metadata, values);
                    imageWrites.incrementAndGet();
                });
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[] {1, 2, 3, 4});

        handler.saveBuffer(bytes, "png", "Image", metadata);

        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(output));
        assertEquals(1, imageWrites.get());
        assertEquals(0, audioWrites.get());
    }

    @Test
    void resolvesAudioPathFromProviderFormatAndInvokesAudioSidecarWriter() throws Exception {
        Path requestedOutput = temporaryDirectory.resolve("generated.wav");
        AtomicReference<Path> sidecarOutput = new AtomicReference<>();
        AtomicInteger imageWrites = new AtomicInteger();
        Map<String, Object> metadata = Map.of("audio_format", "flac");

        MediaOutputCliHandler handler = new MediaOutputCliHandler(
                requestedOutput.toString(),
                null,
                ignored -> "flac",
                (path, values) -> sidecarOutput.set(path),
                (path, values) -> imageWrites.incrementAndGet());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[] {5, 6, 7});

        Path resolved = handler.resolveAudioOutputPath(metadata);
        handler.saveBuffer(bytes, "flac", "Audio", metadata);

        assertEquals(temporaryDirectory.resolve("generated.flac"), resolved);
        assertArrayEquals(new byte[] {5, 6, 7}, Files.readAllBytes(resolved));
        assertEquals(resolved, sidecarOutput.get());
        assertEquals(0, imageWrites.get());
        assertFalse(Files.exists(requestedOutput));
        assertTrue(Files.exists(resolved));
    }
}

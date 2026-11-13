package tech.kayys.aqli.audio.output;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamingAudioChunkRouterTest {

    @TempDir
    Path tempDir;

    @Test
    void routesPcmPreviewToLiveWriterWithoutWritingEncodedOutput() {
        StreamingAudioOutputSession output = new StreamingAudioOutputSession();
        AtomicReference<byte[]> played = new AtomicReference<>();
        StreamingAudioChunkRouter router = new StreamingAudioChunkRouter(
                output,
                ignored -> {
                    throw new AssertionError("PCM preview must not resolve an output path");
                },
                (pcm, metadata) -> played.set(pcm.clone()));

        router.accept("AQID", Map.of("audio_stream_payload", "pcm_s16le"));

        assertArrayEquals(new byte[] {1, 2, 3}, played.get());
        assertFalse(output.hasOutput());
    }

    @Test
    void routesEncodedChunksToFileWhenMetadataRequestsIt() throws Exception {
        Path target = tempDir.resolve("audio.wav");
        List<Path> resolved = new ArrayList<>();
        StreamingAudioOutputSession output = new StreamingAudioOutputSession();
        StreamingAudioChunkRouter router = new StreamingAudioChunkRouter(
                output,
                metadata -> {
                    resolved.add(target);
                    return target;
                },
                (pcm, metadata) -> {
                    throw new AssertionError("Encoded chunks must not be sent to the PCM writer");
                });

        router.accept("aGVsbG8=", Map.of("audio_stream_payload", "encoded"));

        assertEquals(List.of(target), resolved);
        assertTrue(output.hasOutput());
        StreamingAudioOutputSession.Output result = output.finish();
        assertTrue(result instanceof StreamingAudioOutputSession.FileOutput);
        assertTrue(java.nio.file.Files.exists(target));
        output.close();
    }

    @Test
    void buffersEncodedChunksWhenNoFileMetadataIsPresent() throws Exception {
        StreamingAudioOutputSession output = new StreamingAudioOutputSession();
        StreamingAudioChunkRouter router = new StreamingAudioChunkRouter(
                output, ignored -> null, (pcm, metadata) -> {
                    throw new AssertionError("Encoded chunks must not be sent to the PCM writer");
                });

        router.accept("aGVsbG8=", Map.of());

        assertTrue(output.hasOutput());
        StreamingAudioOutputSession.Output result = output.finish();
        assertTrue(result instanceof StreamingAudioOutputSession.BufferedOutput);
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8),
                ((StreamingAudioOutputSession.BufferedOutput) result).bytes());
    }

    @Test
    void ignoresMalformedBase64WithoutFailingTheStream() {
        StreamingAudioOutputSession output = new StreamingAudioOutputSession();
        StreamingAudioChunkRouter router = new StreamingAudioChunkRouter(
                output, ignored -> null, (pcm, metadata) -> {
                    throw new AssertionError("Malformed chunks must not reach the PCM writer");
                });

        router.accept("not-base64%%%", Map.of());

        assertFalse(output.hasOutput());
    }

    @Test
    void classifiesPcmAndEncodedMetadata() {
        assertTrue(StreamingAudioChunkRouter.isPcmStreamChunk(Map.of("audio_stream_preview", true)));
        assertTrue(StreamingAudioChunkRouter.shouldStreamEncodedAudioToFile(Map.of("audio_mime", "audio/wav")));
        assertFalse(StreamingAudioChunkRouter.shouldStreamEncodedAudioToFile(Map.of()));
    }
}

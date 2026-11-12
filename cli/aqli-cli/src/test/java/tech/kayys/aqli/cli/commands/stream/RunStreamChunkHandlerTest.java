package tech.kayys.aqli.cli.commands.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;

class RunStreamChunkHandlerTest {

    @Test
    void routesTextAndTracksFirstAndLastTokenTimes() {
        List<String> text = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        AtomicLong first = new AtomicLong();
        AtomicLong last = new AtomicLong();
        AtomicLong clock = new AtomicLong(100L);
        RunStreamChunkHandler handler = handler(new ByteArrayOutputStream(), chunk -> {},
                text::add, ignored -> {}, ignored -> {}, count, first, last, clock::getAndIncrement);

        handler.accept(StreamingInferenceChunk.textDelta("r", 0, "hello"));
        handler.accept(StreamingInferenceChunk.textDelta("r", 1, " world"));

        assertEquals(List.of("hello", " world"), text);
        assertEquals(2, count.get());
        assertEquals(100L, first.get());
        assertEquals(101L, last.get());
    }

    @Test
    void routesProgressWithoutChangingTokenTiming() {
        List<String> progress = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        AtomicLong first = new AtomicLong();
        AtomicLong last = new AtomicLong();
        RunStreamChunkHandler handler = handler(new ByteArrayOutputStream(), chunk -> {},
                ignored -> {}, progress::add, ignored -> {}, count, first, last, () -> 10L);

        handler.accept(StreamingInferenceChunk.textDelta("r", 0, "[loading]"));

        assertEquals(List.of("[loading]"), progress);
        assertEquals(1, count.get());
        assertEquals(0L, first.get());
        assertEquals(0L, last.get());
    }

    @Test
    void accumulatesImageDeltasAndForwardsAudioChunks() {
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        AtomicReference<StreamingInferenceChunk> audio = new AtomicReference<>();
        RunStreamChunkHandler handler = handler(image, audio::set,
                ignored -> {}, ignored -> {}, ignored -> {},
                new AtomicInteger(), new AtomicLong(), new AtomicLong(), () -> 1L);

        handler.accept(StreamingInferenceChunk.imageChunk("r", 0, "aGVsbG8=", false));
        StreamingInferenceChunk audioChunk = new StreamingInferenceChunk(
                "r", 1, tech.kayys.aljabr.spi.model.ModalityType.AUDIO, null,
                "AQID", false, null, null, java.time.Instant.now(), null, null, null, null);
        handler.accept(audioChunk);

        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), image.toByteArray());
        assertEquals(audioChunk, audio.get());
    }

    @Test
    void forwardsNonEmptyChunkMetadata() {
        AtomicReference<Map<String, Object>> metadata = new AtomicReference<>();
        RunStreamChunkHandler handler = handler(new ByteArrayOutputStream(), chunk -> {},
                ignored -> {}, ignored -> {}, metadata::set,
                new AtomicInteger(), new AtomicLong(), new AtomicLong(), () -> 1L);

        handler.accept(StreamingInferenceChunk.withMetadata("r", 0, "x", Map.of("route", "cpu")));

        assertEquals(Map.of("route", "cpu"), metadata.get());
    }

    private static RunStreamChunkHandler handler(
            ByteArrayOutputStream image,
            java.util.function.Consumer<StreamingInferenceChunk> audio,
            java.util.function.Consumer<String> text,
            java.util.function.Consumer<String> progress,
            java.util.function.Consumer<Map<String, Object>> metadata,
            AtomicInteger count,
            AtomicLong first,
            AtomicLong last,
            java.util.function.LongSupplier clock) {
        return new RunStreamChunkHandler(image, audio, text, progress, metadata,
                count, first, last, clock);
    }
}

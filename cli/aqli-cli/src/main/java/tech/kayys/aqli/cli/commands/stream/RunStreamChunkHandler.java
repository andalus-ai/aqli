package tech.kayys.aqli.cli.commands.stream;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aljabr.spi.model.ModalityType;

/**
 * Routes streamed multimodal chunks to the CLI's output sinks and tracks text-token timing.
 *
 * <p>This is deliberately a CLI adapter: terminal/SSE rendering and output-file handling
 * are supplied by the command, while this class keeps primary and fallback streams identical.
 */
public final class RunStreamChunkHandler {
    private final ByteArrayOutputStream imageBuffer;
    private final Consumer<StreamingInferenceChunk> audioHandler;
    private final Consumer<String> textHandler;
    private final Consumer<String> progressHandler;
    private final Consumer<Map<String, Object>> metadataHandler;
    private final AtomicInteger tokenCount;
    private final AtomicLong firstTokenTime;
    private final AtomicLong lastTokenTime;
    private final LongSupplier clock;

    public RunStreamChunkHandler(
            ByteArrayOutputStream imageBuffer,
            Consumer<StreamingInferenceChunk> audioHandler,
            Consumer<String> textHandler,
            Consumer<String> progressHandler,
            Consumer<Map<String, Object>> metadataHandler,
            AtomicInteger tokenCount,
            AtomicLong firstTokenTime,
            AtomicLong lastTokenTime) {
        this(imageBuffer, audioHandler, textHandler, progressHandler, metadataHandler,
                tokenCount, firstTokenTime, lastTokenTime, System::currentTimeMillis);
    }

    RunStreamChunkHandler(
            ByteArrayOutputStream imageBuffer,
            Consumer<StreamingInferenceChunk> audioHandler,
            Consumer<String> textHandler,
            Consumer<String> progressHandler,
            Consumer<Map<String, Object>> metadataHandler,
            AtomicInteger tokenCount,
            AtomicLong firstTokenTime,
            AtomicLong lastTokenTime,
            LongSupplier clock) {
        this.imageBuffer = Objects.requireNonNull(imageBuffer, "imageBuffer");
        this.audioHandler = Objects.requireNonNull(audioHandler, "audioHandler");
        this.textHandler = Objects.requireNonNull(textHandler, "textHandler");
        this.progressHandler = Objects.requireNonNull(progressHandler, "progressHandler");
        this.metadataHandler = Objects.requireNonNull(metadataHandler, "metadataHandler");
        this.tokenCount = Objects.requireNonNull(tokenCount, "tokenCount");
        this.firstTokenTime = Objects.requireNonNull(firstTokenTime, "firstTokenTime");
        this.lastTokenTime = Objects.requireNonNull(lastTokenTime, "lastTokenTime");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void accept(StreamingInferenceChunk chunk) {
        if (chunk == null) {
            return;
        }
        if (chunk.metadata() != null && !chunk.metadata().isEmpty()) {
            metadataHandler.accept(chunk.metadata());
        }
        if (chunk.modality() == ModalityType.IMAGE) {
            appendImage(chunk.imageDeltaBase64());
            return;
        }
        if (chunk.modality() == ModalityType.AUDIO) {
            audioHandler.accept(chunk);
            return;
        }

        String delta = chunk.getDelta();
        if (delta == null) {
            return;
        }
        boolean progressDelta = delta.startsWith("[") && delta.contains("]");
        if (!progressDelta && !delta.isEmpty()) {
            long now = clock.getAsLong();
            firstTokenTime.compareAndSet(0, now);
            lastTokenTime.set(now);
        }
        if (progressDelta) {
            progressHandler.accept(delta);
        } else {
            textHandler.accept(delta);
        }
        tokenCount.incrementAndGet();
    }

    private void appendImage(String imageDeltaBase64) {
        if (imageDeltaBase64 == null) {
            return;
        }
        try {
            imageBuffer.write(Base64.getDecoder().decode(imageDeltaBase64));
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed partial image chunks, matching the stream's best-effort behavior.
        } catch (java.io.IOException ignored) {
            // ByteArrayOutputStream does not normally throw, but retain best-effort semantics.
        }
    }
}

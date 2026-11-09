package tech.kayys.aqli.cli.commands;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Owns one llama.cpp GGUF native session, including its FFM handle, scratch
 * memory, output-buffer retry policy, metrics access, and deterministic close.
 * Runtime selection and daemon reuse policy remain in {@link GgufFastRun}.
 */
final class LlamaCppGgufSession implements AutoCloseable {
    private static final long ERROR_BUFFER_BYTES = 8192L;
    private static final int OUTPUT_BUFFER_RETRY_LIMIT = 2;

    private final MemorySegment handle;
    private final String backendName;
    private final Arena scratchArena = Arena.ofShared();
    private MemorySegment promptBuffer = MemorySegment.NULL;
    private MemorySegment outputBuffer = MemorySegment.NULL;
    private MemorySegment errorBuffer = MemorySegment.NULL;
    private MemorySegment metricsBuffer = MemorySegment.NULL;
    private long promptBufferCapacity;
    private long outputBufferCapacity;
    private long metricsBufferCapacity;
    private int lastGeneratedTokens;
    private int lastOutputBufferRetries;
    private boolean closed;

    private LlamaCppGgufSession(MemorySegment handle, String backendName) {
        this.handle = handle;
        this.backendName = backendName;
    }

    static LlamaCppGgufSession open(
            Path modelPath,
            int context,
            int batch,
            int microBatch,
            int threads,
            int batchThreads,
            int gpuLayers,
            boolean useMmap,
            boolean useMlock,
            boolean swaFull) throws Throwable {
        LlamaCppGgufNativeBindings binding = LlamaCppGgufNativeBindings.INSTANCE;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment model = arena.allocateFrom(modelPath.toString(), StandardCharsets.UTF_8);
            MemorySegment backendDir = arena.allocateFrom(GgufFastRun.llamaLibDir().toString(), StandardCharsets.UTF_8);
            MemorySegment error = arena.allocate(8192);
            MemorySegment handle = (MemorySegment) binding.open.invokeExact(
                    model,
                    backendDir,
                    context,
                    batch,
                    microBatch,
                    threads,
                    batchThreads,
                    gpuLayers,
                    useMmap ? 1 : 0,
                    useMlock ? 1 : 0,
                    swaFull ? 1 : 0,
                    error,
                    8192L);
            if (isNull(handle)) {
                throw new IllegalStateException(error.getString(0));
            }
            String backendName = "unknown";
            MemorySegment backendPtr = (MemorySegment) binding.backendName.invokeExact(handle);
            if (!isNull(backendPtr)) {
                backendName = backendPtr.reinterpret(128).getString(0);
            }
            return new LlamaCppGgufSession(handle, backendName);
        }
    }

    synchronized String generate(
            String prompt,
            int maxTokens,
            double temperature,
            int topK,
            double topP) throws Throwable {
        ensureOpen();
        MemorySegment promptSegment = promptSegment(prompt);
        MemorySegment error = errorSegment();
        long requestedOutputBytes = GgufFastRun.outputBufferBytesForTokens(maxTokens);
        int retries = 0;
        for (int attempt = 0; attempt <= OUTPUT_BUFFER_RETRY_LIMIT; ++attempt) {
            MemorySegment output = outputSegment(requestedOutputBytes);
            int generated = (int) LlamaCppGgufNativeBindings.INSTANCE.generate.invokeExact(
                    handle,
                    promptSegment,
                    maxTokens,
                    (float) temperature,
                    topK,
                    (float) topP,
                    0,
                    output,
                    outputBufferCapacity,
                    error,
                    ERROR_BUFFER_BYTES);
            if (generated >= 0) {
                lastGeneratedTokens = generated;
                lastOutputBufferRetries = retries;
                return output.getString(0, StandardCharsets.UTF_8);
            }
            String message = error.getString(0);
            if (!GgufFastRun.outputBufferTooSmall(generated, message) || attempt == OUTPUT_BUFFER_RETRY_LIMIT) {
                lastOutputBufferRetries = retries;
                throw new IllegalStateException(message);
            }
            retries++;
            requestedOutputBytes = GgufFastRun.nextOutputBufferBytes(outputBufferCapacity);
        }
        throw new IllegalStateException("GGUF generation failed");
    }

    String backendName() {
        return backendName;
    }

    int lastGeneratedTokens() {
        return lastGeneratedTokens;
    }

    synchronized Optional<String> metrics() throws Throwable {
        return metrics(false, "cold");
    }

    synchronized Optional<String> metrics(boolean warmSession, String reuseKind) throws Throwable {
        ensureOpen();
        LlamaCppGgufNativeBindings binding = LlamaCppGgufNativeBindings.INSTANCE;
        if (binding.metrics == null) {
            return Optional.empty();
        }
        MemorySegment output = metricsSegment();
        int written = (int) binding.metrics.invokeExact(handle, output, metricsBufferCapacity);
        if (written <= 0) {
            return Optional.empty();
        }
        return Optional.of(output.getString(0, StandardCharsets.UTF_8)
                + ", warmSession=" + warmSession
                + ", sessionReuse=" + metricToken(reuseKind, warmSession ? "warm" : "cold")
                + ", javaOutputBufferBytes=" + outputBufferCapacity
                + ", javaOutputRetries=" + lastOutputBufferRetries);
    }

    private static String metricToken(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized.replaceAll("[^a-z0-9_-]+", "_");
    }

    private MemorySegment promptSegment(String prompt) {
        byte[] bytes = prompt.getBytes(StandardCharsets.UTF_8);
        long required = bytes.length + 1L;
        if (promptBufferCapacity < required) {
            promptBufferCapacity = nextScratchCapacity(required);
            promptBuffer = scratchArena.allocate(promptBufferCapacity);
        }
        promptBuffer.asSlice(0, bytes.length).copyFrom(MemorySegment.ofArray(bytes));
        promptBuffer.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
        return promptBuffer;
    }

    private MemorySegment outputSegment(long requested) {
        if (outputBufferCapacity < requested) {
            outputBufferCapacity = nextScratchCapacity(requested);
            outputBuffer = scratchArena.allocate(outputBufferCapacity);
        }
        return outputBuffer;
    }

    private MemorySegment errorSegment() {
        if (isNull(errorBuffer)) {
            errorBuffer = scratchArena.allocate(ERROR_BUFFER_BYTES);
        }
        return errorBuffer;
    }

    private MemorySegment metricsSegment() {
        long requested = 4096L;
        if (metricsBufferCapacity < requested) {
            metricsBufferCapacity = requested;
            metricsBuffer = scratchArena.allocate(metricsBufferCapacity);
        }
        return metricsBuffer;
    }

    private static long nextScratchCapacity(long requested) {
        long capacity = 1024L;
        while (capacity < requested && capacity <= Long.MAX_VALUE / 2L) {
            capacity <<= 1;
        }
        return Math.max(requested, capacity);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("GGUF native session is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            LlamaCppGgufNativeBindings.INSTANCE.close.invokeExact(handle);
        } catch (Throwable ignored) {
        } finally {
            scratchArena.close();
        }
    }

    private static boolean isNull(MemorySegment segment) {
        return segment == null || segment.equals(MemorySegment.NULL) || segment.address() == 0L;
    }

    }

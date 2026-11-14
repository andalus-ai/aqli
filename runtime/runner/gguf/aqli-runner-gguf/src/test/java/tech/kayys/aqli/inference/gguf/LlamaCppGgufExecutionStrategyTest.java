package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LlamaCppGgufExecutionStrategyTest {

    @Test
    void executesSessionAndCapturesBackendTokenAndMetricResults() throws Exception {
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        when(session.generateWithMetrics("hello", 32, 0.7d, 40, 0.9d, true, "compatible"))
                .thenAnswer(invocation -> new LlamaCppGgufSession.GenerationResult(
                        "world", 7, System.nanoTime(), Optional.of("decode_ms=12")));
        when(session.backendName()).thenReturn("metal");

        LlamaCppGgufExecutionStrategy.Execution result =
                new LlamaCppGgufExecutionStrategy().execute(
                        session, "hello", 32, 0.7d, 40, 0.9d, true, "compatible");

        assertEquals("world", result.output());
        assertEquals("metal", result.backendName());
        assertEquals(7, result.outputTokens());
        assertEquals(Optional.of("decode_ms=12"), result.nativeMetrics());
        assertTrue(result.executionNanos() >= 0);
        verify(session).generateWithMetrics("hello", 32, 0.7d, 40, 0.9d, true, "compatible");
    }

    @Test
    void missingMetricsDoNotFailInferenceResult() throws Exception {
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        when(session.generateWithMetrics("hello", 2, 0.0d, 1, 1.0d, false, "cold"))
                .thenAnswer(invocation -> new LlamaCppGgufSession.GenerationResult(
                        "", 0, System.nanoTime(), Optional.empty()));
        when(session.backendName()).thenReturn("cpu");

        LlamaCppGgufExecutionStrategy.Execution result =
                new LlamaCppGgufExecutionStrategy().execute(
                        session, "hello", 2, 0.0d, 1, 1.0d, false, "cold");

        assertEquals("", result.output());
        assertTrue(result.nativeMetrics().isEmpty());
    }
}

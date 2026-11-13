package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
        when(session.generate("hello", 32, 0.7d, 40, 0.9d)).thenReturn("world");
        when(session.backendName()).thenReturn("metal");
        when(session.lastGeneratedTokens()).thenReturn(7);
        when(session.metrics(true, "compatible")).thenReturn(Optional.of("decode_ms=12"));

        LlamaCppGgufExecutionStrategy.Execution result =
                new LlamaCppGgufExecutionStrategy().execute(
                        session, "hello", 32, 0.7d, 40, 0.9d, true, "compatible");

        assertEquals("world", result.output());
        assertEquals("metal", result.backendName());
        assertEquals(7, result.outputTokens());
        assertEquals(Optional.of("decode_ms=12"), result.nativeMetrics());
        assertTrue(result.executionNanos() >= 0);
        verify(session).generate("hello", 32, 0.7d, 40, 0.9d);
        verify(session).metrics(true, "compatible");
    }

    @Test
    void serializesGenerationAndMetricsAsOneOperationForTheSameSession() throws Exception {
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        CountDownLatch firstMetricsEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstMetrics = new CountDownLatch(1);
        CountDownLatch secondGenerationEntered = new CountDownLatch(1);

        when(session.generate(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    if ("second".equals(prompt)) {
                        secondGenerationEntered.countDown();
                    }
                    return prompt + "-output";
                });
        when(session.metrics(false, "cold")).thenAnswer(invocation -> {
            firstMetricsEntered.countDown();
            if (!releaseFirstMetrics.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release first metrics call");
            }
            return Optional.empty();
        });
        when(session.backendName()).thenReturn("cpu");
        when(session.lastGeneratedTokens()).thenReturn(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            LlamaCppGgufExecutionStrategy strategy = new LlamaCppGgufExecutionStrategy();
            Future<LlamaCppGgufExecutionStrategy.Execution> first = executor.submit(
                    () -> strategy.execute(session, "first", 4, 0.0d, 1, 1.0d, false, "cold"));
            assertTrue(firstMetricsEntered.await(1, TimeUnit.SECONDS));

            Future<LlamaCppGgufExecutionStrategy.Execution> second = executor.submit(
                    () -> strategy.execute(session, "second", 4, 0.0d, 1, 1.0d, false, "cold"));

            assertFalse(secondGenerationEntered.await(150, TimeUnit.MILLISECONDS));
            releaseFirstMetrics.countDown();

            assertEquals("first-output", first.get(1, TimeUnit.SECONDS).output());
            assertEquals("second-output", second.get(1, TimeUnit.SECONDS).output());
            assertTrue(secondGenerationEntered.await(1, TimeUnit.SECONDS));
        } finally {
            releaseFirstMetrics.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void metricsFailureDoesNotFailInferenceResult() throws Exception {
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        when(session.generate("hello", 2, 0.0d, 1, 1.0d)).thenReturn("");
        when(session.backendName()).thenReturn("cpu");
        when(session.metrics(false, "cold")).thenThrow(new IllegalStateException("metrics unavailable"));

        LlamaCppGgufExecutionStrategy.Execution result =
                new LlamaCppGgufExecutionStrategy().execute(
                        session, "hello", 2, 0.0d, 1, 1.0d, false, "cold");

        assertEquals("", result.output());
        assertTrue(result.nativeMetrics().isEmpty());
    }
}

package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

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

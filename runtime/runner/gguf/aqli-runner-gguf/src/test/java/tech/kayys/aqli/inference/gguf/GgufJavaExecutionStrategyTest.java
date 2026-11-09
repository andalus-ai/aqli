package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import tech.kayys.aljabr.gguf.api.GgufBackend;
import tech.kayys.aljabr.spi.inference.InferenceRequest;
import tech.kayys.aljabr.spi.inference.InferenceResponse;

class GgufJavaExecutionStrategyTest {

    @Test
    void closesOneShotSessionAfterExecution() throws Exception {
        GgufBackend backend = mock(GgufBackend.class);
        GgufJavaBackendSession session = GgufJavaBackendSession.owned(backend);
        InferenceRequest request = mock(InferenceRequest.class);
        InferenceResponse response = mock(InferenceResponse.class);
        when(backend.execute(request)).thenReturn(response);

        GgufJavaExecutionStrategy.Execution result = new GgufJavaExecutionStrategy().execute(
                Path.of("model.gguf"), request, ignored -> session, true);

        assertEquals(response, result.response());
        verify(backend).execute(request);
        verify(backend).close();
    }

    @Test
    void retainsDaemonSessionAfterExecution() throws Exception {
        GgufBackend backend = mock(GgufBackend.class);
        GgufJavaBackendSession session = GgufJavaBackendSession.owned(backend);
        InferenceRequest request = mock(InferenceRequest.class);
        when(backend.execute(request)).thenReturn(mock(InferenceResponse.class));

        new GgufJavaExecutionStrategy().execute(
                Path.of("model.gguf"), request, ignored -> session, false);

        verify(backend).execute(request);
        verify(backend, never()).close();
    }
}

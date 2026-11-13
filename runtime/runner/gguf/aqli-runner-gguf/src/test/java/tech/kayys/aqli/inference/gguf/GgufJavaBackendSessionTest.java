package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import tech.kayys.aljabr.gguf.api.GgufBackend;
import tech.kayys.aljabr.spi.inference.InferenceRequest;
import tech.kayys.aljabr.spi.inference.InferenceResponse;

class GgufJavaBackendSessionTest {

    @Test
    void closesOwnedBackendExactlyOnce() throws Exception {
        GgufBackend backend = mock(GgufBackend.class);
        GgufJavaBackendSession session = GgufJavaBackendSession.owned(backend);

        session.close();
        session.close();

        verify(backend, times(1)).close();
        assertThrows(IllegalStateException.class, session::backend);
    }

    @Test
    void executesRequestThroughOpenSession() throws Exception {
        GgufBackend backend = mock(GgufBackend.class);
        GgufJavaBackendSession session = GgufJavaBackendSession.owned(backend);
        InferenceRequest request = mock(InferenceRequest.class);
        InferenceResponse response = mock(InferenceResponse.class);
        when(backend.execute(request)).thenReturn(response);

        assertEquals(response, session.execute(request));

        verify(backend).execute(request);
    }

    @Test
    void rejectsExecutionAfterSessionClose() throws Exception {
        GgufBackend backend = mock(GgufBackend.class);
        GgufJavaBackendSession session = GgufJavaBackendSession.owned(backend);
        session.close();

        assertThrows(IllegalStateException.class, () -> session.execute(mock(InferenceRequest.class)));
    }
}

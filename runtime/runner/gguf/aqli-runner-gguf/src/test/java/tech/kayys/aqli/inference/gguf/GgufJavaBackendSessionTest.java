package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import tech.kayys.aljabr.gguf.api.GgufBackend;

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
}

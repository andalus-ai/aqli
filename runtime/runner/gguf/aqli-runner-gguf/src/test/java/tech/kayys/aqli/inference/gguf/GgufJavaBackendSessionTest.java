package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import tech.kayys.aljabr.gguf.api.GgufBackend;

class GgufJavaBackendSessionTest {

    @Test
    void borrowedSessionDoesNotCloseCacheOwnedBackend() throws Exception {
        GgufBackend backend = mock(GgufBackend.class);
        GgufJavaBackendSession session = GgufJavaBackendSession.borrowed(backend);

        assertSame(backend, session.backend());
        session.close();

        verify(backend, never()).close();
    }

    @Test
    void closedSessionRejectsFurtherBackendAccess() throws Exception {
        GgufBackend backend = mock(GgufBackend.class);
        GgufJavaBackendSession session = GgufJavaBackendSession.borrowed(backend);

        session.close();

        assertThrows(IllegalStateException.class, session::backend);
    }
}

package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class LlamaCppGgufSessionAcquirerTest {

    @Test
    void delegatesSessionAcquisitionToDaemonCacheWithResolvedParameters() throws Exception {
        Path modelPath = Path.of("models/model.gguf");
        LlamaCppGgufLibraryPaths libraryPaths = new LlamaCppGgufLibraryPaths(
                Path.of("bridge.dylib"), Path.of("llama"));
        GgufSessionCache cache = mock(GgufSessionCache.class);
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        GgufSessionCache.SessionLease expected =
                new GgufSessionCache.SessionLease(session, true, false, "compatible");
        LlamaCppGgufSessionParameters parameters = new LlamaCppGgufSessionParameters(
                4096, 1024, 512, 8, 4, 32, true, false, true);
        when(cache.acquire(modelPath, parameters)).thenReturn(expected);

        GgufSessionCache.SessionLease actual =
                new LlamaCppGgufSessionAcquirer(libraryPaths)
                        .acquire(modelPath, parameters, Optional.of(cache));

        assertSame(expected, actual);
        verify(cache).acquire(modelPath, parameters);
    }
}

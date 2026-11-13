package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GgufSessionCacheTest {

    @Test
    void acceptsWarmSessionThatCoversRequestedCapacityAndMatchesExecutionSettings() {
        GgufSessionCache.SessionKey warm = key(8192, 512, 256, 8, 4, 99, true, false, false);
        GgufSessionCache.SessionKey requested = key(4096, 256, 128, 8, 4, 99, true, false, false);

        assertTrue(GgufSessionCache.isCompatibleWarmSession(warm, requested));
        assertTrue(GgufSessionCache.smallerCompatibleSession(requested, warm));
    }

    @Test
    void rejectsWarmSessionWhenAnyExecutionSettingIsIncompatible() {
        GgufSessionCache.SessionKey warm = key(8192, 512, 256, 8, 4, 99, true, false, false);

        assertFalse(GgufSessionCache.isCompatibleWarmSession(
                warm, key(4096, 256, 128, 8, 4, 0, true, false, false)));
        assertFalse(GgufSessionCache.isCompatibleWarmSession(
                warm, key(4096, 256, 128, 8, 4, 99, false, false, false)));
        assertFalse(GgufSessionCache.isCompatibleWarmSession(
                warm, key(4096, 256, 128, 8, 4, 99, true, false, true)));
    }

    @Test
    void oneShotLeaseClosesItsOwnedSession() {
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        GgufSessionCache.SessionLease lease =
                new GgufSessionCache.SessionLease(session, false, true, "cold");

        lease.close();

        verify(session, times(1)).close();
    }

    @Test
    void cachedLeaseDoesNotCloseCacheOwnedSession() {
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        GgufSessionCache.SessionLease lease =
                new GgufSessionCache.SessionLease(session, true, false, "exact");

        lease.close();

        verify(session, times(0)).close();
        assertEquals("exact", lease.reuseKind());
    }


    @Test
    void closeIsIdempotentAndRejectsFurtherAcquisition() {
        GgufSessionCache cache = new GgufSessionCache(
                () -> 2,
                ignored -> { },
                new LlamaCppGgufLibraryPaths(
                        Path.of("/native/bridge.dylib"),
                        Path.of("/native/llama")));

        cache.close();
        cache.close();

        assertThrows(IllegalStateException.class,
                () -> cache.acquireJavaBackend(Path.of("/models/model.gguf")));
        assertThrows(IllegalStateException.class,
                () -> cache.acquire(
                        Path.of("/models/model.gguf"),
                        new LlamaCppGgufSessionParameters(
                                4096, 256, 128, 8, 4, 0, true, false, false)));
    }

    private static GgufSessionCache.SessionKey key(
            int context,
            int batch,
            int microBatch,
            int threads,
            int batchThreads,
            int gpuLayers,
            boolean useMmap,
            boolean useMlock,
            boolean swaFull) {
        return new GgufSessionCache.SessionKey(
                Path.of("/models/model.gguf"),
                new LlamaCppGgufSessionParameters(
                        context, batch, microBatch, threads, batchThreads, gpuLayers,
                        useMmap, useMlock, swaFull));
    }
}

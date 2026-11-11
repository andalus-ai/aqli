package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void oneShotLeaseClosesItsSessionExactlyOnce() {
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        GgufSessionCache.SessionLease lease =
                new GgufSessionCache.SessionLease(session, false, true, "cold");

        lease.close();
        lease.close();

        verify(session, times(2)).close();
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
                context,
                batch,
                microBatch,
                threads,
                batchThreads,
                gpuLayers,
                useMmap,
                useMlock,
                swaFull);
    }
}

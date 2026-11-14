package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import tech.kayys.aljabr.gguf.api.GgufBackend;

class GgufSessionCacheTest {

    @Test
    void acceptsWarmSessionThatCoversRequestedCapacityAndMatchesExecutionSettings() {
        GgufSessionCache.SessionKey warm = key(8192, 512, 256, 8, 4, 99, true, false, false);
        GgufSessionCache.SessionKey requested = key(4096, 256, 128, 8, 4, 99, true, false, false);

        assertTrue(GgufSessionCache.isCompatibleWarmSession(warm, requested));
        assertTrue(GgufSessionCache.lessOverprovisioned(requested, warm));
    }

    @Test
    void compatibleSelectionPrefersCandidateThatUsesNoMoreCapacityInAnyDimension() {
        GgufSessionCache.SessionKey larger = key(8192, 512, 256, 8, 4, 99, true, false, false);
        GgufSessionCache.SessionKey smaller = key(4096, 256, 128, 8, 4, 99, true, false, false);

        assertEquals(smaller, GgufSessionCache.selectCompatibleSession(List.of(larger, smaller)));
    }

    @Test
    void compatibleSelectionUsesMostRecentlyUsedWhenCapacityTradeoffsAreIncomparable() {
        GgufSessionCache.SessionKey largerContext = key(8192, 256, 128, 8, 4, 99, true, false, false);
        GgufSessionCache.SessionKey largerBatch = key(4096, 512, 128, 8, 4, 99, true, false, false);

        assertFalse(GgufSessionCache.lessOverprovisioned(largerContext, largerBatch));
        assertFalse(GgufSessionCache.lessOverprovisioned(largerBatch, largerContext));
        assertEquals(largerBatch,
                GgufSessionCache.selectCompatibleSession(List.of(largerContext, largerBatch)));
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

    @Test
    void sessionLimitAlwaysRetainsCapacityForTheSessionBeingAcquired() {
        assertEquals(1, GgufSessionCache.effectiveMaxSessions(0));
        assertEquals(1, GgufSessionCache.effectiveMaxSessions(-3));
        assertEquals(2, GgufSessionCache.effectiveMaxSessions(2));
    }

    @Test
    void evictionDefersClosingSessionUntilItsBorrowedLeaseIsReleased() throws Exception {
        LlamaCppGgufSession first = mock(LlamaCppGgufSession.class);
        LlamaCppGgufSession second = mock(LlamaCppGgufSession.class);
        AtomicInteger opened = new AtomicInteger();
        GgufSessionCache cache = cache(1, (path, parameters, paths) ->
                opened.getAndIncrement() == 0 ? first : second);

        GgufSessionCache.SessionLease firstLease = cache.acquire(
                Path.of("/models/first.gguf"), parameters(99));
        GgufSessionCache.SessionLease secondLease = cache.acquire(
                Path.of("/models/second.gguf"), parameters(99));

        verify(first, never()).close();
        firstLease.close();
        verify(first).close();

        secondLease.close();
        cache.close();
        verify(second).close();
    }

    @Test
    void cacheShutdownDefersClosingBorrowedSessionUntilLastLeaseCloses() throws Exception {
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        GgufSessionCache cache = cache(1, (path, parameters, paths) -> session);
        GgufSessionCache.SessionLease first = cache.acquire(
                Path.of("/models/model.gguf"), parameters(99));
        GgufSessionCache.SessionLease second = cache.acquire(
                Path.of("/models/model.gguf"), parameters(99));

        cache.close();
        verify(session, never()).close();

        first.close();
        first.close();
        verify(session, never()).close();

        second.close();
        verify(session).close();
        second.close();
        verify(session, times(1)).close();
    }

    @Test
    void cachedLeaseReleaseDoesNotCloseSessionWhileItRemainsInCache() throws Exception {
        LlamaCppGgufSession session = mock(LlamaCppGgufSession.class);
        GgufSessionCache cache = cache(2, (path, parameters, paths) -> session);
        GgufSessionCache.SessionLease lease = cache.acquire(
                Path.of("/models/model.gguf"), parameters(99));

        lease.close();

        verify(session, never()).close();
        cache.close();
        verify(session).close();
    }

    @Test
    void cacheShutdownDefersClosingJavaBackendUntilLastLeaseCloses() throws Exception {
        GgufBackend backend = mock(GgufBackend.class);
        GgufJavaBackendSession session = GgufJavaBackendSession.owned(backend);
        GgufSessionCache cache = new GgufSessionCache(
                () -> 2,
                ignored -> { },
                new LlamaCppGgufLibraryPaths(
                        Path.of("/native/bridge.dylib"), Path.of("/native/llama")),
                (path, parameters, paths) -> mock(LlamaCppGgufSession.class),
                path -> session);

        GgufJavaBackendSession.Lease first = cache.acquireJavaBackend(Path.of("/models/model.gguf"));
        GgufJavaBackendSession.Lease second = cache.acquireJavaBackend(Path.of("/models/model.gguf"));

        cache.close();
        verify(backend, never()).close();

        first.close();
        first.close();
        verify(backend, never()).close();

        second.close();
        second.close();
        verify(backend, times(1)).close();
    }

    @Test
    void javaBackendCacheEvictsLeastRecentlyUsedSessionAtConfiguredCapacity() throws Exception {
        GgufBackend firstBackend = mock(GgufBackend.class);
        GgufBackend secondBackend = mock(GgufBackend.class);
        GgufBackend thirdBackend = mock(GgufBackend.class);
        AtomicInteger opened = new AtomicInteger();
        GgufSessionCache cache = new GgufSessionCache(
                () -> 2,
                ignored -> { },
                new LlamaCppGgufLibraryPaths(
                        Path.of("/native/bridge.dylib"), Path.of("/native/llama")),
                (path, parameters, paths) -> mock(LlamaCppGgufSession.class),
                path -> switch (opened.getAndIncrement()) {
                    case 0 -> GgufJavaBackendSession.owned(firstBackend);
                    case 1 -> GgufJavaBackendSession.owned(secondBackend);
                    default -> GgufJavaBackendSession.owned(thirdBackend);
                });

        cache.acquireJavaBackend(Path.of("/models/first.gguf")).close();
        cache.acquireJavaBackend(Path.of("/models/second.gguf")).close();
        cache.acquireJavaBackend(Path.of("/models/first.gguf")).close();
        cache.acquireJavaBackend(Path.of("/models/third.gguf")).close();

        verify(firstBackend, never()).close();
        verify(secondBackend).close();
        verify(thirdBackend, never()).close();

        cache.close();
        verify(firstBackend).close();
        verify(thirdBackend).close();
    }

    @Test
    void javaBackendEvictionDefersCloseUntilBorrowedLeaseIsReleased() throws Exception {
        GgufBackend firstBackend = mock(GgufBackend.class);
        GgufBackend secondBackend = mock(GgufBackend.class);
        AtomicInteger opened = new AtomicInteger();
        GgufSessionCache cache = new GgufSessionCache(
                () -> 1,
                ignored -> { },
                new LlamaCppGgufLibraryPaths(
                        Path.of("/native/bridge.dylib"), Path.of("/native/llama")),
                (path, parameters, paths) -> mock(LlamaCppGgufSession.class),
                path -> GgufJavaBackendSession.owned(
                        opened.getAndIncrement() == 0 ? firstBackend : secondBackend));

        GgufJavaBackendSession.Lease firstLease =
                cache.acquireJavaBackend(Path.of("/models/first.gguf"));
        GgufJavaBackendSession.Lease secondLease =
                cache.acquireJavaBackend(Path.of("/models/second.gguf"));

        verify(firstBackend, never()).close();
        firstLease.close();
        verify(firstBackend).close();

        secondLease.close();
        cache.close();
        verify(secondBackend).close();
    }

    @Test
    void loggerFailureDoesNotInterruptNativeSessionAcquisitionOrEviction() throws Exception {
        LlamaCppGgufSession first = mock(LlamaCppGgufSession.class);
        LlamaCppGgufSession second = mock(LlamaCppGgufSession.class);
        AtomicInteger opened = new AtomicInteger();
        GgufSessionCache cache = new GgufSessionCache(
                () -> 1,
                message -> { throw new IllegalStateException("logger unavailable"); },
                new LlamaCppGgufLibraryPaths(
                        Path.of("/native/bridge.dylib"), Path.of("/native/llama")),
                (path, parameters, paths) -> opened.getAndIncrement() == 0 ? first : second);

        GgufSessionCache.SessionLease firstLease =
                cache.acquire(Path.of("/models/first.gguf"), parameters(99));
        GgufSessionCache.SessionLease secondLease =
                cache.acquire(Path.of("/models/second.gguf"), parameters(99));

        firstLease.close();
        verify(first).close();
        secondLease.close();
        cache.close();
        verify(second).close();
    }

    @Test
    void loggerFailureDoesNotInterruptJavaBackendAcquisitionOrEviction() throws Exception {
        GgufBackend firstBackend = mock(GgufBackend.class);
        GgufBackend secondBackend = mock(GgufBackend.class);
        AtomicInteger opened = new AtomicInteger();
        GgufSessionCache cache = new GgufSessionCache(
                () -> 1,
                message -> { throw new IllegalStateException("logger unavailable"); },
                new LlamaCppGgufLibraryPaths(
                        Path.of("/native/bridge.dylib"), Path.of("/native/llama")),
                (path, parameters, paths) -> mock(LlamaCppGgufSession.class),
                path -> GgufJavaBackendSession.owned(
                        opened.getAndIncrement() == 0 ? firstBackend : secondBackend));

        GgufJavaBackendSession.Lease firstLease =
                cache.acquireJavaBackend(Path.of("/models/first.gguf"));
        GgufJavaBackendSession.Lease secondLease =
                cache.acquireJavaBackend(Path.of("/models/second.gguf"));

        firstLease.close();
        verify(firstBackend).close();
        secondLease.close();
        cache.close();
        verify(secondBackend).close();
    }

    private static GgufSessionCache cache(
            int capacity, GgufSessionCache.SessionOpener opener) {
        return new GgufSessionCache(
                () -> capacity,
                ignored -> { },
                new LlamaCppGgufLibraryPaths(
                        Path.of("/native/bridge.dylib"), Path.of("/native/llama")),
                opener);
    }

    private static LlamaCppGgufSessionParameters parameters(int gpuLayers) {
        return new LlamaCppGgufSessionParameters(
                4096, 256, 128, 8, 4, gpuLayers, true, false, false);
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

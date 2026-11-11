package tech.kayys.aqli.provider.litert;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteRTEngineCacheTest {

    @Test
    void reusesEngineAndReturnsBorrowedLeases() {
        LiteRTEngineCache<TrackedResource> cache = new LiteRTEngineCache<>();
        AtomicInteger creations = new AtomicInteger();

        LiteRTEngineLease<TrackedResource> first = cache.acquire("model|cpu", () -> {
            creations.incrementAndGet();
            return new LiteRTEngineLease<>(new TrackedResource(), "CPU", false, null, false, false);
        });
        LiteRTEngineLease<TrackedResource> second = cache.acquire("model|cpu", () -> {
            creations.incrementAndGet();
            return new LiteRTEngineLease<>(new TrackedResource(), "CPU", false, null, false, false);
        });

        assertNotSame(first, second);
        assertSame(first.engine(), second.engine());
        assertFalse(first.reused());
        assertTrue(second.reused());
        assertEquals(1, creations.get());
        assertEquals(1, cache.size());

        first.close();
        second.close();
        assertEquals(0, first.engine().closeCount.get());
        cache.close();
        assertEquals(1, first.engine().closeCount.get());
    }

    @Test
    void sharesPrewarmProgressAcrossBorrowedLeases() {
        LiteRTEngineLease<TrackedResource> owner =
                new LiteRTEngineLease<>(new TrackedResource(), "CPU", false, null, false, false);
        LiteRTEngineLease<TrackedResource> borrowed = owner.borrowed(true);

        assertEquals(3, owner.remainingPrewarmIterations(3));
        borrowed.markPrewarmed(2);
        assertEquals(1, owner.remainingPrewarmIterations(3));
        owner.markPrewarmed(1);
        assertEquals(0, borrowed.remainingPrewarmIterations(3));
        owner.forceClose();
    }

    @Test
    void closesKeepAliveBeforeEngineAndOnlyOnce() {
        LiteRTEngineLease<TrackedResource> lease =
                new LiteRTEngineLease<>(new TrackedResource(), "CPU", false, null, false, false);
        AtomicInteger keepAliveClosed = new AtomicInteger();
        lease.attachKeepAlive(keepAliveClosed::incrementAndGet);

        lease.forceClose();
        lease.forceClose();

        assertEquals(1, keepAliveClosed.get());
        assertEquals(1, lease.engine().closeCount.get());
    }

    @Test
    void rejectsAcquisitionAfterCacheClose() {
        LiteRTEngineCache<TrackedResource> cache = new LiteRTEngineCache<>();
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.acquire("model", () ->
                new LiteRTEngineLease<>(new TrackedResource(), "CPU", false, null, false, false)));
    }

    private static final class TrackedResource implements AutoCloseable {
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }
}

/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.session;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.safetensor.engine.generation.kv.BlockManager;
import tech.kayys.aqli.safetensor.engine.generation.kv.KVCacheManager;
import tech.kayys.aqli.safetensor.engine.generation.paged.PagedKVSessionRegistry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConversationSessionManagerLifecycleTest {

    @Test
    void replacingSessionIdClosesDisplacedKvCache() {
        ConversationSessionManager manager = manager();
        TestKvCache first = new TestKvCache();
        TestKvCache replacement = new TestKvCache();

        manager.create("conversation", "model-a", new int[] {1}, first);
        manager.create("conversation", "model-a", new int[] {2}, replacement);

        assertEquals(1, manager.activeSessions());
        assertEquals(1, first.closeCount.get());
        assertEquals(0, replacement.closeCount.get());

        manager.stop();
        assertEquals(1, replacement.closeCount.get());
    }

    @Test
    void managerShutdownOnlyClosesSessionsOwnedByThatInstance() {
        ConversationSessionManager first = manager();
        ConversationSessionManager second = manager();
        TestKvCache firstCache = new TestKvCache();
        TestKvCache secondCache = new TestKvCache();

        first.create("first", "model-a", new int[] {1}, firstCache);
        second.create("second", "model-a", new int[] {2}, secondCache);

        first.stop();

        assertEquals(1, firstCache.closeCount.get());
        assertEquals(0, secondCache.closeCount.get());
        assertEquals(1, second.activeSessions());

        second.stop();
        assertEquals(1, secondCache.closeCount.get());
    }

    @Test
    void closingRetainedSessionDefersKvReleaseUntilLeaseEnds() {
        ConversationSessionManager manager = manager();
        TestKvCache cache = new TestKvCache();
        manager.create("conversation", "model-a", new int[] {1}, cache);

        ConversationSessionManager.SessionLease lease =
                manager.acquire("conversation", "model-a").orElseThrow();

        manager.close("conversation");

        assertEquals(0, manager.activeSessions());
        assertEquals(0, cache.closeCount.get());
        assertSame(cache, lease.session().kvCache());

        lease.close();
        lease.close();

        assertEquals(1, cache.closeCount.get());
        manager.stop();
    }

    @Test
    void replacingKvCacheWaitsForActiveLeaseToFinish() {
        ConversationSessionManager manager = manager();
        TestKvCache original = new TestKvCache();
        TestKvCache replacement = new TestKvCache();
        manager.create("conversation", "model-a", new int[] {1}, original);
        ConversationSessionManager.SessionLease lease =
                manager.acquire("conversation", "model-a").orElseThrow();

        ConversationSessionManager.ConversationSession updated =
                manager.recordSnapshot("conversation", "model-a", new int[] {1, 2}, replacement);

        assertSame(original, updated.kvCache());
        assertEquals(0, original.closeCount.get());
        assertEquals(0, replacement.closeCount.get());

        lease.close();

        assertEquals(1, original.closeCount.get());
        assertSame(replacement, updated.kvCache());

        manager.stop();
        assertEquals(1, replacement.closeCount.get());
    }

    @Test
    void capacityEvictionCountsOnlySessionsActuallyRemoved() throws InterruptedException {
        ConversationSessionManager manager = manager();
        manager.maxSessions = 1;
        TestKvCache first = new TestKvCache();
        TestKvCache second = new TestKvCache();

        manager.create("first", "model-a", new int[] {1}, first);
        Thread.sleep(10);
        manager.create("second", "model-a", new int[] {2}, second);

        assertEquals(1, manager.activeSessions());
        assertEquals(1, manager.totalExpired());
        assertEquals(1, first.closeCount.get());
        assertEquals(0, second.closeCount.get());

        manager.stop();
    }

    private static ConversationSessionManager manager() {
        ConversationSessionManager manager = new ConversationSessionManager();
        manager.sessionRegistry = new PagedKVSessionRegistry();
        manager.idleTimeoutMinutes = 30;
        manager.maxSessions = 100;
        manager.maxTokensPerSession = 32768;
        return manager;
    }

    private static final class TestKvCache extends KVCacheManager.KVCacheSession {
        private final AtomicInteger closeCount = new AtomicInteger();

        private TestKvCache() {
            super(1, new BlockManager());
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }
}

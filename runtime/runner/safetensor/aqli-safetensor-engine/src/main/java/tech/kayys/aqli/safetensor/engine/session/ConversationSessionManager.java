/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 *
 * ConversationSessionManager.java
 * ─────────────────────────────────
 * Persists per-conversation KV cache state across multiple HTTP requests.
 *
 * Problem
 * ═══════
 * Without session persistence, every /v1/chat/completions request re-prefills
 * the entire conversation history from scratch.  For a 10-turn chat with an
 * average of 200 tokens per turn, turn 10 requires a 1800-token prefill —
 * 9× the work of just processing the new message.
 *
 * Solution
 * ════════
 * Store the encoded token IDs AND the corresponding KV cache blocks for each
 * conversation.  On the next turn:
 *   1. Encode only the NEW messages (assistant response + new user message).
 *   2. Re-use the existing KV blocks for the prefix — skip prefill for them.
 *   3. Run prefill ONLY for the new tokens.
 *   4. Decode as normal.
 *
 * This reduces prefill cost from O(total_tokens) to O(new_tokens_this_turn).
 *
 * Session lifecycle
 * ═════════════════
 *   CREATE  — on first request with a session_id (or auto-generated)
 *   UPDATE  — after each turn: append new tokens + KV blocks
 *   EXPIRE  — after idleTimeoutMinutes of inactivity (default: 30)
 *   DELETE  — on explicit /v1/sessions/{id} DELETE, or on model unload
 *
 * KV block ownership
 * ══════════════════
 * The session owns a reference to the KV blocks from the PagedKVCache pool.
 * When the session expires, the blocks are returned to the pool.
 * The PagedKVSessionRegistry tracks these for eviction under memory pressure.
 *
 * Session ID
 * ══════════
 * Passed as request.sessionId or X-Session-Id HTTP header.
 * If absent, a new session is created and the ID is returned in the response
 * metadata as "session_id".
 *
 * Conversation reconstruction
 * ═══════════════════════════
 * When a session is found, we don't re-encode the full message history.
 * Instead we track cumulative token IDs and KV block offsets, and only
 * process the delta since last turn.
 */
package tech.kayys.aqli.safetensor.engine.session;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.arc.Arc;
import tech.kayys.aqli.safetensor.engine.generation.kv.KVCacheManager;
import tech.kayys.aqli.safetensor.engine.generation.kv.KVCacheManager.KVCacheSession;
import tech.kayys.aqli.safetensor.engine.generation.paged.PagedKVSessionRegistry;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-conversation KV cache session manager for multi-turn inference.
 *
 * <p>
 * Usage in {@code DirectInferenceEngine.generate()}:
 * 
 * <pre>{@code
 * // Look up existing session
 * Optional<ConversationSession> session = sessionMgr.find(sessionId, modelKey);
 *
 * if (session.isPresent()) {
 *     // Only encode new messages (delta since last turn)
 *     int[] deltaIds = tokenizer.encode(newMessagesOnly, true);
 *     // Prepend session's accumulated IDs for context
 *     int[] allIds = session.get().extendWith(deltaIds);
 *     // Run forward pass starting from the cached position
 *     logits = forwardPass.prefill(deltaIds, weights, config, arch, session.get().kvCache());
 *     session.get().advance(deltaIds.length);
 * } else {
 *     // Fresh start — prefill everything
 *     int[] allIds = tokenizer.encode(allMessages, true);
 *     logits = forwardPass.prefill(allIds, weights, config, arch, newKvCache);
 *     session = sessionMgr.create(sessionId, modelKey, allIds, newKvCache);
 * }
 * }</pre>
 */
@ApplicationScoped
public class ConversationSessionManager {

    private static final Logger log = Logger.getLogger(ConversationSessionManager.class);
    private static final int DEFAULT_IDLE_TIMEOUT_MINUTES = 30;
    private static final int DEFAULT_MAX_SESSIONS = 1000;
    private static final int DEFAULT_MAX_TOKENS_PER_SESSION = 32768;

    @ConfigProperty(name = "aqli.session.idle-timeout-minutes", defaultValue = "30")
    int idleTimeoutMinutes;

    @ConfigProperty(name = "aqli.session.max-sessions", defaultValue = "1000")
    int maxSessions;

    @ConfigProperty(name = "aqli.session.max-tokens-per-session", defaultValue = "32768")
    int maxTokensPerSession;

    @Inject
    KVCacheManager kvCacheManager;
    @Inject
    PagedKVSessionRegistry sessionRegistry;

    /** Sessions owned by this application-scoped manager instance. */
    private final ConcurrentHashMap<String, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();

    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "aqli-session-cleaner");
        t.setDaemon(true);
        return t;
    });

    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger totalExpired = new AtomicInteger(0);

    private PagedKVSessionRegistry sessionRegistry() {
        if (sessionRegistry != null) {
            return sessionRegistry;
        }
        try {
            if (Arc.container() != null) {
                var instance = Arc.container().instance(PagedKVSessionRegistry.class);
                if (instance.isAvailable()) {
                    sessionRegistry = instance.get();
                }
            }
        } catch (Exception ignored) {
        }
        return sessionRegistry;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @jakarta.annotation.PostConstruct
    void start() {
        int idleTimeout = resolvedIdleTimeoutMinutes();
        int sessionLimit = resolvedMaxSessions();
        cleaner.scheduleAtFixedRate(this::evictExpired,
                idleTimeout, idleTimeout, TimeUnit.MINUTES);
        log.infof("ConversationSessionManager: started (timeout=%dm, max=%d)",
                idleTimeout, sessionLimit);
    }

    @PreDestroy
    void stop() {
        cleaner.shutdownNow();
        synchronized (lifecycleLock) {
            List<ConversationSession> ownedSessions = new ArrayList<>(sessions.values());
            sessions.clear();
            ownedSessions.forEach(session -> {
                deregister(session.sessionId());
                session.close();
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Find an existing session by ID and model key.
     *
     * @param sessionId unique session identifier
     * @param modelKey  key identifying which model is loaded
     * @return the session if found, valid, and for the same model
     */
    public Optional<ConversationSession> find(String sessionId, String modelKey) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        synchronized (lifecycleLock) {
            ConversationSession session = sessions.get(sessionId);
            if (session == null) {
                return Optional.empty();
            }
            if (!session.modelKey().equals(modelKey) || isExpired(session) || session.isClosed()) {
                if (sessions.remove(sessionId, session)) {
                    deregister(sessionId);
                    session.close();
                    if (isExpired(session)) {
                        totalExpired.incrementAndGet();
                    }
                    if (!session.modelKey().equals(modelKey)) {
                        log.debugf("Session %s found but model changed — discarding", sessionId);
                    }
                }
                return Optional.empty();
            }
            session.touch();
            return Optional.of(session);
        }
    }

    /**
     * Acquire a use lease that keeps the underlying KV cache alive until the
     * caller closes the lease. Eviction removes the session from lookup
     * immediately, but defers native KV release while leases remain active.
     */
    public Optional<SessionLease> acquire(String sessionId, String modelKey) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        synchronized (lifecycleLock) {
            ConversationSession session = sessions.get(sessionId);
            if (session == null) {
                return Optional.empty();
            }
            if (!session.modelKey().equals(modelKey) || isExpired(session) || session.isClosed()) {
                if (sessions.remove(sessionId, session)) {
                    deregister(sessionId);
                    session.close();
                }
                return Optional.empty();
            }
            SessionLease lease = session.tryAcquire();
            if (lease == null) {
                return Optional.empty();
            }
            session.touch();
            return Optional.of(lease);
        }
    }

    /**
     * Probe conversation execution state without exposing the internal session
     * object to the planner.
     */
    public ConversationExecutionState probe(String sessionId, String modelKey) {
        if (sessionId == null || sessionId.isBlank()) {
            return ConversationExecutionState.noneRequested();
        }
        synchronized (lifecycleLock) {
            ConversationSession session = sessions.get(sessionId);
            if (session == null) {
                return ConversationExecutionState.miss(
                        sessionId,
                        modelKey,
                        "No cached conversation execution state was found for the requested session");
            }
            if (!session.modelKey().equals(modelKey)) {
                if (sessions.remove(sessionId, session)) {
                    deregister(sessionId);
                    session.close();
                }
                return ConversationExecutionState.modelMismatch(
                        sessionId,
                        modelKey,
                        session.modelKey(),
                        session.totalTokens(),
                        toLongArray(session.tokenIds()),
                        "Conversation session exists, but it belongs to a different model");
            }
            if (isExpired(session) || session.isClosed()) {
                if (sessions.remove(sessionId, session)) {
                    deregister(sessionId);
                    session.close();
                    totalExpired.incrementAndGet();
                }
                return ConversationExecutionState.expired(
                        sessionId,
                        modelKey,
                        session.totalTokens(),
                        toLongArray(session.tokenIds()),
                        "Conversation session existed, but its cached execution state expired");
            }
            session.touch();
            return ConversationExecutionState.hit(
                    sessionId,
                    modelKey,
                    session.totalTokens(),
                    toLongArray(session.tokenIds()),
                    session.pendingReplayTokenId(),
                    session.kvCache() != null,
                    "Conversation session has active cached execution state");
        }
    }

    /**
     * Create a new conversation session after completing a fresh prefill.
     *
     * @param sessionId    session identifier (from request or auto-generated)
     * @param modelKey     model key from DirectInferenceEngine
     * @param prefixTokens all token IDs that have been prefilled
     * @param kvCache      the KV cache session populated by prefill
     * @return the created session
     */
    public ConversationSession create(String sessionId, String modelKey,
            int[] prefixTokens,
            KVCacheSession kvCache) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        Objects.requireNonNull(modelKey, "modelKey");
        Objects.requireNonNull(prefixTokens, "prefixTokens");
        synchronized (lifecycleLock) {
            int sessionLimit = resolvedMaxSessions();
            if (sessions.size() >= sessionLimit && !sessions.containsKey(sessionId)) {
                evictOldestLocked(sessions.size() - sessionLimit + 1);
            }

            ConversationSession session = new ConversationSession(
                    sessionId, modelKey, prefixTokens, kvCache, Instant.now());
            ConversationSession displaced = sessions.put(sessionId, session);
            if (displaced != null) {
                displaced.close();
                deregister(sessionId);
            }
            register(sessionId);
            totalCreated.incrementAndGet();

            log.debugf("ConversationSession created: id=%s model=%s tokens=%d",
                    sessionId, modelKey, prefixTokens.length);
            return session;
        }
    }

    /**
     * Record or replace token-only conversation state when a newer execution
     * snapshot is available but KV state is not yet resumable through the new
     * backend lifecycle.
     */
    public ConversationSession recordSnapshot(String sessionId, String modelKey, int[] allTokens) {
        return recordSnapshot(sessionId, modelKey, allTokens, null, null);
    }

    public ConversationSession recordSnapshot(
            String sessionId,
            String modelKey,
            int[] allTokens,
            KVCacheSession kvCache,
            Integer pendingReplayTokenId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        synchronized (lifecycleLock) {
            ConversationSession existing = sessions.get(sessionId);
            if (existing != null && existing.modelKey().equals(modelKey) && !existing.isClosed()) {
                existing.replaceTokens(allTokens);
                existing.replaceKvCache(kvCache);
                existing.replacePendingReplayTokenId(pendingReplayTokenId);
                existing.touch();
                return existing;
            }
            if (existing != null && sessions.remove(sessionId, existing)) {
                existing.close();
                deregister(sessionId);
            }
            ConversationSession created = create(sessionId, modelKey, allTokens, kvCache);
            created.replacePendingReplayTokenId(pendingReplayTokenId);
            return created;
        }
    }

    /**
     * Extend an existing session with new token IDs after a completed turn.
     *
     * @param sessionId session to update
     * @param newTokens token IDs generated this turn (assistant + user combined)
     */
    public void extend(String sessionId, int[] newTokens) {
        synchronized (lifecycleLock) {
            ConversationSession session = sessions.get(sessionId);
            if (session == null || session.isClosed()) {
                return;
            }
            int maxTokens = resolvedMaxTokensPerSession();
            if (session.totalTokens() + newTokens.length > maxTokens) {
                log.infof("Session %s exceeded max tokens (%d) — truncating prefix",
                        sessionId, maxTokens);
                session.truncateToHalf();
            }
            session.extend(newTokens);
            session.touch();
        }
    }

    /**
     * Explicitly close and remove a session.
     */
    public void close(String sessionId) {
        synchronized (lifecycleLock) {
            ConversationSession session = sessions.remove(sessionId);
            if (session != null) {
                deregister(sessionId);
                session.close();
            }
        }
    }

    /** Number of active sessions. */
    public int activeSessions() {
        return sessions.size();
    }

    /** Total sessions created since startup. */
    public int totalCreated() {
        return totalCreated.get();
    }

    /** Total sessions expired/evicted since startup. */
    public int totalExpired() {
        return totalExpired.get();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void evictExpired() {
        synchronized (lifecycleLock) {
            Instant cutoff = expirationCutoff();
            int evicted = 0;
            for (Map.Entry<String, ConversationSession> entry : sessions.entrySet()) {
                ConversationSession session = entry.getValue();
                if (session.lastAccessTime().isBefore(cutoff)
                        && sessions.remove(entry.getKey(), session)) {
                    deregister(entry.getKey());
                    session.close();
                    evicted++;
                }
            }
            if (evicted > 0) {
                totalExpired.addAndGet(evicted);
                log.infof("ConversationSessionManager: evicted %d expired sessions", evicted);
            }
        }
    }

    private void evictOldest(int count) {
        synchronized (lifecycleLock) {
            evictOldestLocked(count);
        }
    }

    private void evictOldestLocked(int count) {
        if (count <= 0) {
            return;
        }
        int evicted = 0;
        List<Map.Entry<String, ConversationSession>> oldest = sessions.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                        Comparator.comparing(ConversationSession::lastAccessTime)))
                .limit(count)
                .toList();
        for (Map.Entry<String, ConversationSession> entry : oldest) {
            if (sessions.remove(entry.getKey(), entry.getValue())) {
                deregister(entry.getKey());
                entry.getValue().close();
                evicted++;
            }
        }
        if (evicted > 0) {
            totalExpired.addAndGet(evicted);
        }
    }

    private void register(String sessionId) {
        PagedKVSessionRegistry registry = sessionRegistry;
        if (registry != null) {
            registry.register(sessionId, null, PagedKVSessionRegistry.SessionPriority.NORMAL);
        }
    }

    private void deregister(String sessionId) {
        PagedKVSessionRegistry registry = sessionRegistry;
        if (registry != null) {
            registry.deregister(sessionId);
        }
    }

    private boolean isExpired(ConversationSession s) {
        return s.lastAccessTime().isBefore(expirationCutoff());
    }

    private Instant expirationCutoff() {
        return Instant.now().minusSeconds(resolvedIdleTimeoutMinutes() * 60L);
    }

    private int resolvedIdleTimeoutMinutes() {
        return idleTimeoutMinutes > 0 ? idleTimeoutMinutes : DEFAULT_IDLE_TIMEOUT_MINUTES;
    }

    private int resolvedMaxSessions() {
        return maxSessions > 0 ? maxSessions : DEFAULT_MAX_SESSIONS;
    }

    private int resolvedMaxTokensPerSession() {
        return maxTokensPerSession > 0 ? maxTokensPerSession : DEFAULT_MAX_TOKENS_PER_SESSION;
    }

    private long[] toLongArray(int[] tokenIds) {
        long[] out = new long[tokenIds.length];
        for (int i = 0; i < tokenIds.length; i++) {
            out[i] = tokenIds[i];
        }
        return out;
    }

    /**
     * A temporary pin on a retained conversation session. Closing the manager
     * entry does not release its native KV allocation until the final lease ends.
     */
    public static final class SessionLease implements AutoCloseable {
        private final ConversationSession session;
        private boolean released;

        private SessionLease(ConversationSession session) {
            this.session = session;
        }

        public ConversationSession session() {
            synchronized (session) {
                if (released) {
                    throw new IllegalStateException("Conversation session lease is closed");
                }
                return session;
            }
        }

        @Override
        public void close() {
            synchronized (session) {
                if (!released) {
                    released = true;
                    session.releaseLease();
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ConversationSession value type
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A single multi-turn conversation session.
     *
     * <p>
     * Holds the accumulated token IDs and the associated KV cache session.
     */
    public static final class ConversationSession implements AutoCloseable {

        private final String sessionId;
        private final String modelKey;
        private volatile KVCacheSession kvCache;
        private volatile Instant lastAccess;
        private volatile int[] tokenIds; // cumulative token IDs
        private volatile Integer pendingReplayTokenId;
        private volatile boolean closed = false;
        private int activeLeases;
        private boolean closeRequested;
        private KVCacheSession pendingKvCache;
        private boolean hasPendingKvCache;

        ConversationSession(String sessionId, String modelKey,
                int[] initialTokens,
                KVCacheSession kvCache,
                Instant createdAt) {
            this.sessionId = sessionId;
            this.modelKey = modelKey;
            this.kvCache = kvCache;
            this.tokenIds = Arrays.copyOf(initialTokens, initialTokens.length);
            this.lastAccess = createdAt;
        }

        public String sessionId() {
            return sessionId;
        }

        public String modelKey() {
            return modelKey;
        }

        public KVCacheSession kvCache() {
            return kvCache;
        }

        public Instant lastAccessTime() {
            return lastAccess;
        }

        public int[] tokenIds() {
            return Arrays.copyOf(tokenIds, tokenIds.length);
        }

        public int totalTokens() {
            return tokenIds.length;
        }

        public Integer pendingReplayTokenId() {
            return pendingReplayTokenId;
        }

        void touch() {
            this.lastAccess = Instant.now();
        }

        /**
         * Extend the session with new token IDs (from a completed decode step).
         */
        void extend(int[] newTokens) {
            int[] combined = new int[tokenIds.length + newTokens.length];
            System.arraycopy(tokenIds, 0, combined, 0, tokenIds.length);
            System.arraycopy(newTokens, 0, combined, tokenIds.length, newTokens.length);
            this.tokenIds = combined;
        }

        void replaceTokens(int[] allTokens) {
            this.tokenIds = Arrays.copyOf(allTokens, allTokens.length);
        }

        void replacePendingReplayTokenId(Integer pendingReplayTokenId) {
            this.pendingReplayTokenId = pendingReplayTokenId;
        }

        synchronized SessionLease tryAcquire() {
            if (closeRequested) {
                return null;
            }
            activeLeases++;
            return new SessionLease(this);
        }

        synchronized void releaseLease() {
            if (activeLeases <= 0) {
                throw new IllegalStateException("Conversation session lease underflow");
            }
            activeLeases--;
            if (activeLeases == 0) {
                if (closeRequested) {
                    closeKvCache();
                } else if (hasPendingKvCache) {
                    closeCurrentKvCache();
                    kvCache = pendingKvCache;
                    pendingKvCache = null;
                    hasPendingKvCache = false;
                }
            }
        }

        synchronized void replaceKvCache(KVCacheSession replacement) {
            if (this.kvCache == replacement) {
                return;
            }
            if (activeLeases > 0) {
                // A pending replacement is not visible to active leases; dispose
                // of an earlier unpublished replacement before superseding it.
                if (hasPendingKvCache && pendingKvCache != null && pendingKvCache != replacement) {
                    try {
                        pendingKvCache.close();
                    } catch (Exception ignored) {
                    }
                }
                pendingKvCache = replacement;
                hasPendingKvCache = true;
                return;
            }
            closeCurrentKvCache();
            this.kvCache = replacement;
        }

        /**
         * Truncate the accumulated token IDs to half their current length,
         * keeping the most recent tokens (sliding window context management).
         */
        void truncateToHalf() {
            int half = tokenIds.length / 2;
            this.tokenIds = Arrays.copyOfRange(tokenIds, half, tokenIds.length);
            // Note: KV cache blocks for dropped tokens will be reclaimed on next eviction
        }

        /**
         * Build the combined token array for a new turn:
         * existing prefix + new delta tokens.
         */
        public int[] extendWith(int[] deltaTokens) {
            int[] combined = new int[tokenIds.length + deltaTokens.length];
            System.arraycopy(tokenIds, 0, combined, 0, tokenIds.length);
            System.arraycopy(deltaTokens, 0, combined, tokenIds.length, deltaTokens.length);
            return combined;
        }

        @Override
        public synchronized void close() {
            closeRequested = true;
            if (activeLeases == 0) {
                closeKvCache();
            }
        }

        private void closeKvCache() {
            if (closed) {
                return;
            }
            closed = true;
            closeCurrentKvCache();
            if (pendingKvCache != null && pendingKvCache != kvCache) {
                try {
                    pendingKvCache.close();
                } catch (Exception ignored) {
                }
            }
            pendingKvCache = null;
            hasPendingKvCache = false;
            pendingReplayTokenId = null;
        }

        private void closeCurrentKvCache() {
            if (kvCache != null) {
                try {
                    kvCache.close();
                } catch (Exception ignored) {
                }
                kvCache = null;
            }
        }

        public synchronized boolean isClosed() {
            return closeRequested || closed;
        }
    }
}

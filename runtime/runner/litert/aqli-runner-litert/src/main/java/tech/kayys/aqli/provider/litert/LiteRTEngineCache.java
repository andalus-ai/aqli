package tech.kayys.aqli.provider.litert;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Owns reusable LiteRT engine instances for a long-lived runner process.
 *
 * <p>Routing and cache-key policy are supplied by the caller. This cache only owns engine
 * lifetime, borrowed leases, and deterministic shutdown.</p>
 */
public final class LiteRTEngineCache<T extends AutoCloseable> implements AutoCloseable {
    private final Map<String, LiteRTEngineLease<T>> engines = new HashMap<>();
    private boolean closed;

    public synchronized LiteRTEngineLease<T> acquire(
            String key,
            Supplier<LiteRTEngineLease<T>> engineFactory) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(engineFactory, "engineFactory");
        if (closed) {
            throw new IllegalStateException("LiteRT engine cache is closed");
        }
        LiteRTEngineLease<T> existing = engines.get(key);
        if (existing != null) {
            return existing.borrowed(true);
        }
        LiteRTEngineLease<T> created = Objects.requireNonNull(
                engineFactory.get(), "engineFactory returned null");
        engines.put(key, created);
        return created.borrowed(false);
    }

    public synchronized int size() {
        return engines.size();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (LiteRTEngineLease<T> engine : engines.values()) {
            engine.forceClose();
        }
        engines.clear();
    }
}

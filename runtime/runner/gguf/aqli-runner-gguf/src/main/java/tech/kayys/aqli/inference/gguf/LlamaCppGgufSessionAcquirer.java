package tech.kayys.aqli.inference.gguf;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Acquires a llama.cpp session lease using either daemon-owned cache reuse or
 * an independently owned one-shot session.
 *
 * <p>The caller supplies the cache when daemon reuse is desired. Cache lifetime
 * and shutdown remain owned by {@link GgufSessionCache}; one-shot leases close
 * their native session when the lease is closed.</p>
 */
public final class LlamaCppGgufSessionAcquirer {
    private final LlamaCppGgufLibraryPaths libraryPaths;

    public LlamaCppGgufSessionAcquirer(LlamaCppGgufLibraryPaths libraryPaths) {
        this.libraryPaths = Objects.requireNonNull(libraryPaths, "libraryPaths");
    }

    public GgufSessionCache.SessionLease acquire(
            Path modelPath,
            LlamaCppGgufSessionParameters parameters,
            Optional<GgufSessionCache> sessionCache) throws Exception {
        Objects.requireNonNull(modelPath, "modelPath");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(sessionCache, "sessionCache");

        if (sessionCache.isPresent()) {
            return sessionCache.get().acquire(modelPath, parameters);
        }

        return GgufSessionCache.SessionLease.open(modelPath, parameters, libraryPaths);
    }
}

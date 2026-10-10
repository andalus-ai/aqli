package tech.kayys.aqli.promptcache;

import io.quarkus.runtime.annotations.RegisterForReflection;
import tech.kayys.aqli.cache.CachedKVEntry;
import tech.kayys.aqli.cache.PrefixHash;
import tech.kayys.aqli.cache.PromptCacheStats;

/**
 * GraalVM native-image reflection registration for prompt-cache types.
 *
 * <p>Mirrors the pattern of {@code NativeImageFeature} in {@code aqli-engine-core}.
 * Required for serialization of records (Jackson, JSON-B) in native mode.
 */
@RegisterForReflection(targets = {
        CachedKVEntry.class,
        PrefixHash.class,
        PromptCacheStats.class
})
public class NativeImagePromptCacheFeature {
    // No body needed — annotation drives native-image registration
}

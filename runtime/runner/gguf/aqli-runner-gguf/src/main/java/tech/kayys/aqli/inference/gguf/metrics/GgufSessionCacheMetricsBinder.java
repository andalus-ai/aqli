package tech.kayys.aqli.inference.gguf.metrics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import tech.kayys.aqli.inference.gguf.GgufSessionCache;

/**
 * Binds GGUF session-cache snapshots to Micrometer without adding telemetry
 * concerns to the cache implementation itself.
 *
 * <p>All meters are pool-scoped and deliberately avoid model-path tags to keep
 * metric cardinality bounded. The cache must outlive the registry meters that
 * reference it.</p>
 */
public final class GgufSessionCacheMetricsBinder implements MeterBinder {

    private static final String PREFIX = "aqli.gguf.cache.";

    private final GgufSessionCache cache;

    public GgufSessionCacheMetricsBinder(GgufSessionCache cache) {
        this.cache = cache;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(PREFIX + "entries", cache,
                        value -> value.snapshot().nativeEntries())
                .description("Number of resident native GGUF sessions")
                .tag("pool", "native")
                .register(registry);
        Gauge.builder(PREFIX + "entries", cache,
                        value -> value.snapshot().javaBackendEntries())
                .description("Number of resident Java GGUF backend sessions")
                .tag("pool", "java")
                .register(registry);

        Gauge.builder(PREFIX + "active.leases", cache,
                        value -> value.snapshot().activeNativeLeases())
                .description("Number of active borrowed native GGUF session leases")
                .tag("pool", "native")
                .register(registry);
        Gauge.builder(PREFIX + "active.leases", cache,
                        value -> value.snapshot().activeJavaBackendLeases())
                .description("Number of active borrowed Java GGUF backend leases")
                .tag("pool", "java")
                .register(registry);

        FunctionCounter.builder(PREFIX + "hits", cache,
                        value -> value.snapshot().nativeHits())
                .description("Cumulative native GGUF session-cache hits")
                .tag("pool", "native")
                .register(registry);
        FunctionCounter.builder(PREFIX + "hits", cache,
                        value -> value.snapshot().javaBackendHits())
                .description("Cumulative Java GGUF backend-cache hits")
                .tag("pool", "java")
                .register(registry);

        FunctionCounter.builder(PREFIX + "misses", cache,
                        value -> value.snapshot().nativeMisses())
                .description("Cumulative native GGUF session-cache misses")
                .tag("pool", "native")
                .register(registry);
        FunctionCounter.builder(PREFIX + "misses", cache,
                        value -> value.snapshot().javaBackendMisses())
                .description("Cumulative Java GGUF backend-cache misses")
                .tag("pool", "java")
                .register(registry);

        FunctionCounter.builder(PREFIX + "evictions", cache,
                        value -> value.snapshot().nativeEvictions())
                .description("Cumulative native GGUF session-cache evictions")
                .tag("pool", "native")
                .register(registry);
        FunctionCounter.builder(PREFIX + "evictions", cache,
                        value -> value.snapshot().javaBackendEvictions())
                .description("Cumulative Java GGUF backend-cache evictions")
                .tag("pool", "java")
                .register(registry);
    }
}

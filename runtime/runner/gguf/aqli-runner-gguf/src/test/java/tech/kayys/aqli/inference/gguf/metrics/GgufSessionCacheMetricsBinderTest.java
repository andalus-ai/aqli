package tech.kayys.aqli.inference.gguf.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.inference.gguf.GgufSessionCache;

class GgufSessionCacheMetricsBinderTest {

    @Test
    void bindsPoolOccupancyLeasesAndCumulativeLifecycleCounters() {
        GgufSessionCache cache = mock(GgufSessionCache.class);
        when(cache.snapshot()).thenReturn(new GgufSessionCache.CacheSnapshot(
                3, 2,
                4, 5,
                7, 11, 13,
                17, 19, 23));

        try (SimpleMeterRegistry registry = new SimpleMeterRegistry()) {
            new GgufSessionCacheMetricsBinder(cache).bindTo(registry);

            assertEquals(3.0, registry.get("aqli.gguf.cache.entries")
                    .tag("pool", "native").gauge().value());
            assertEquals(2.0, registry.get("aqli.gguf.cache.entries")
                    .tag("pool", "java").gauge().value());

            assertEquals(4.0, registry.get("aqli.gguf.cache.active.leases")
                    .tag("pool", "native").gauge().value());
            assertEquals(5.0, registry.get("aqli.gguf.cache.active.leases")
                    .tag("pool", "java").gauge().value());

            assertEquals(7.0, registry.get("aqli.gguf.cache.hits")
                    .tag("pool", "native").functionCounter().count());
            assertEquals(17.0, registry.get("aqli.gguf.cache.hits")
                    .tag("pool", "java").functionCounter().count());

            assertEquals(11.0, registry.get("aqli.gguf.cache.misses")
                    .tag("pool", "native").functionCounter().count());
            assertEquals(19.0, registry.get("aqli.gguf.cache.misses")
                    .tag("pool", "java").functionCounter().count());

            assertEquals(13.0, registry.get("aqli.gguf.cache.evictions")
                    .tag("pool", "native").functionCounter().count());
            assertEquals(23.0, registry.get("aqli.gguf.cache.evictions")
                    .tag("pool", "java").functionCounter().count());
        }
    }
}

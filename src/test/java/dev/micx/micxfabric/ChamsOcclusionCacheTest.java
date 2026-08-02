package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChamsOcclusionCacheTest {
    @Test
    void reusesOneResultForWorldTickAndEntity() {
        ChamsOcclusionCache cache = new ChamsOcclusionCache();
        Object world = new Object();
        AtomicInteger calls = new AtomicInteger();

        assertEquals(true, cache.getOrCompute(world, 7, 42, () -> {
            calls.incrementAndGet();
            return true;
        }));
        assertEquals(true, cache.getOrCompute(world, 7, 42, () -> {
            calls.incrementAndGet();
            return false;
        }));

        assertEquals(1, calls.get());
        assertEquals(1, cache.size());
    }

    @Test
    void clearsOnTickWorldAndEntityIdentityChanges() {
        ChamsOcclusionCache cache = new ChamsOcclusionCache();
        Object worldA = new Object();
        Object worldB = new Object();
        AtomicInteger calls = new AtomicInteger();
        ChamsOcclusionCache.BooleanSupplier compute = () -> {
            calls.incrementAndGet();
            return calls.get() % 2 == 0;
        };

        cache.getOrCompute(worldA, 1, 9, compute);
        cache.getOrCompute(worldA, 1, 10, compute);
        cache.getOrCompute(worldA, 2, 9, compute);
        cache.getOrCompute(worldB, 2, 9, compute);

        assertEquals(4, calls.get());
        assertEquals(1, cache.size());
    }

    @Test
    void clearIsIdempotentAndAllowsEntityIdReuse() {
        ChamsOcclusionCache cache = new ChamsOcclusionCache();
        Object world = new Object();
        AtomicInteger calls = new AtomicInteger();

        cache.getOrCompute(world, 3, 12, () -> {
            calls.incrementAndGet();
            return true;
        });
        cache.clear();
        cache.getOrCompute(world, 3, 12, () -> {
            calls.incrementAndGet();
            return false;
        });

        assertEquals(2, calls.get());
        assertEquals(1, cache.size());
    }
}

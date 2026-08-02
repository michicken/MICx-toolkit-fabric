package dev.micx.micxfabric;

import java.util.HashMap;
import java.util.Map;

/** Per-world, per-tick occlusion cache that does not leak entity ids across worlds. */
final class ChamsOcclusionCache {
    private final Map<Integer, Boolean> byEntityId = new HashMap<>();
    private Object worldIdentity;
    private long tick = Long.MIN_VALUE;

    boolean getOrCompute(Object world, long worldTick, int entityId, BooleanSupplier supplier) {
        if (world != worldIdentity || worldTick != tick) {
            worldIdentity = world;
            tick = worldTick;
            byEntityId.clear();
        }
        Boolean cached = byEntityId.get(entityId);
        if (cached != null) return cached;
        boolean result = supplier.getAsBoolean();
        byEntityId.put(entityId, result);
        return result;
    }

    void clear() {
        worldIdentity = null;
        tick = Long.MIN_VALUE;
        byEntityId.clear();
    }

    int size() {
        return byEntityId.size();
    }

    @FunctionalInterface
    interface BooleanSupplier {
        boolean getAsBoolean();
    }
}

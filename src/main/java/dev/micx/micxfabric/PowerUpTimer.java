package dev.micx.micxfabric;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic power-up drop/activation timer with immutable snapshots. */
public final class PowerUpTimer {
    public static final long DROP_LIFETIME_MS = 60_000L;
    public static final long CORRELATION_WINDOW_MS = 2_200L;

    public record Drop(String kind, int entityId, int round, long firstSeenAt, long expiresAt) {
    }

    public record Active(String kind, long activatedAt, long expiresAt) {
    }

    private final Map<Integer, Drop> drops = new LinkedHashMap<>();
    private final Map<String, Active> active = new LinkedHashMap<>();

    public void clear() {
        drops.clear();
        active.clear();
    }

    public void observeDrop(String kind, int entityId, int round, long now) {
        if (kind == null || kind.isBlank() || entityId < 0 || now < 0) return;
        Drop existing = drops.get(entityId);
        if (existing != null) return;
        drops.put(entityId, new Drop(kind, entityId, round, now, now + DROP_LIFETIME_MS));
    }

    public void removeDrop(int entityId) {
        drops.remove(entityId);
    }

    public void activate(String kind, int durationSeconds, long now) {
        if (kind == null || kind.isBlank() || durationSeconds < 1 || durationSeconds > 120 || now < 0) return;
        Active previous = active.get(kind);
        long expiresAt = now + durationSeconds * 1000L;
        if (previous != null && now - previous.activatedAt() <= CORRELATION_WINDOW_MS) {
            expiresAt = previous.expiresAt();
        } else {
            active.put(kind, new Active(kind, now, expiresAt));
            return;
        }
        active.put(kind, new Active(kind, previous.activatedAt(), expiresAt));
    }

    public void expire(long now) {
        active.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        drops.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    public Map<Integer, Drop> dropsSnapshot() {
        return Map.copyOf(drops);
    }

    public Map<String, Active> activeSnapshot() {
        return Map.copyOf(active);
    }

    public long dropRemainingMs(int entityId, long now) {
        Drop drop = drops.get(entityId);
        return drop == null ? 0L : Math.max(0L, drop.expiresAt() - now);
    }

    public long activeRemainingMs(String kind, long now) {
        Active value = active.get(kind);
        return value == null ? 0L : Math.max(0L, value.expiresAt() - now);
    }
}

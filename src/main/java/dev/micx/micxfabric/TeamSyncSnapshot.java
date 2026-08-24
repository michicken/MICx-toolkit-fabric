package dev.micx.micxfabric;

/** Thread-safe mutable view of one remote teammate's latest TeamSync state. */
public final class TeamSyncSnapshot {
    public final String name;
    public volatile long receivedMs;
    public volatile double posX, posY, posZ;
    public volatile float yaw, pitch;
    public volatile float hp = -1.0f, maxHp = -1.0f, absorption;
    public volatile String status = "unknown";
    public volatile int ping = -1;
    public volatile int targetId = Integer.MIN_VALUE;
    public volatile String targetType;
    public volatile double targetX, targetY, targetZ;
    public volatile float targetHp = -1.0f;
    public volatile String targetName;
    public volatile double pingX, pingY, pingZ;
    public volatile String pingLabel;
    public volatile long pingUntilMs;
    public volatile String hotbar7Item;
    public volatile String hotbar7Name;
    public volatile String hotbar8Item;
    public volatile String hotbar8Name;
    public volatile String hotbar9Item;
    public volatile String hotbar9Name;
    public volatile long hotbarUpdatedMs;

    public TeamSyncSnapshot(String name) {
        this.name = name;
    }

    public boolean fresh(long now, long ttlMs) {
        return receivedMs > 0L && now >= receivedMs && now - receivedMs <= ttlMs;
    }

    public boolean hasTarget(long now, long ttlMs) {
        return targetId != Integer.MIN_VALUE && fresh(now, ttlMs);
    }

    public boolean hasPing(long now) {
        return pingUntilMs > now;
    }
}

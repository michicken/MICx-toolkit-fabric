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

    /** 槽位 5 技能（LR/Heal）状态通道。 */
    public volatile boolean skillKnown;
    public volatile TeamSkillTracker.State skillState = TeamSkillTracker.State.UNKNOWN;
    public volatile String skillName;
    public volatile int skillRemainingSeconds;
    public volatile long skillUpdatedMs;

    /** LR 释放通道：队友自放 LR 的精确释放/截止时间。 */
    public volatile boolean lrKnown;
    public volatile long lrReleasedAtMs;
    public volatile long lrReadyAtMs;
    public volatile long lrUpdatedMs;

    /** 最近一次 LR 命中徽章（瞬时事件，供 TeammateHP 卡片短暂展示）。 */
    public volatile int lastLrStruckCount = -1;
    public volatile String lastLrStruckName;
    public volatile long lastLrHitAtMs;

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

    public boolean isSkillFresh(long ttlMs) {
        return skillKnown && skillUpdatedMs > 0
                && System.currentTimeMillis() - skillUpdatedMs < ttlMs;
    }

    public void clearSkill(long updatedMs) {
        skillKnown = false;
        skillState = TeamSkillTracker.State.UNKNOWN;
        skillName = null;
        skillRemainingSeconds = 0;
        skillUpdatedMs = updatedMs;
    }

    /** LR 通道数据是否仍新鲜（发送端持续上报 LR 属性）。 */
    public boolean isLrFresh(long ttlMs) {
        return System.currentTimeMillis() - lrUpdatedMs < ttlMs;
    }

    /** LR 剩余冷却秒数；就绪或未知返回 0（基于精确截止时间）。 */
    public long lrRemainingSec(long now) {
        if (!lrKnown || lrReadyAtMs <= 0) return 0;
        return Math.max(0, (lrReadyAtMs - now + 999L) / 1000L);
    }

    /** LR 是否就绪（有通道数据且冷却已结束/显式清除）。 */
    public boolean isLrReady(long now) {
        return lrKnown && lrReadyAtMs <= 0;
    }

    public void clearLr(long updatedMs) {
        lrKnown = false;
        lrReleasedAtMs = 0L;
        lrReadyAtMs = 0L;
        lrUpdatedMs = updatedMs;
    }

    public boolean isLrHitFresh(long ttlMs) {
        return lastLrHitAtMs > 0 && System.currentTimeMillis() - lastLrHitAtMs < ttlMs;
    }
}

package dev.micx.micxfabric;

/** Pure timing rules used to reconcile chat and delayed sidebar life-state signals. */
final class TeammateLifeStateRules {
    static final long ALIVE_GRACE_MS = 750L;
    static final long ALIVE_CONFIRM_MS = 200L;
    static final long DEAD_CONFIRM_MS = 200L;
    static final long BLEEDOUT_GUARD_MS = 1_000L;

    private TeammateLifeStateRules() {
    }

    static boolean shouldClearDown(long downSince, boolean sidebarDownSeen,
                                   long aliveCandidateSince, long now) {
        if (downSince < 0L || aliveCandidateSince < 0L) return false;
        boolean sidebarCaughtUp = sidebarDownSeen || now - downSince >= ALIVE_GRACE_MS;
        return sidebarCaughtUp && now - aliveCandidateSince >= ALIVE_CONFIRM_MS;
    }

    static boolean protectsActiveDown(long downSince, long now, long bleedoutMs) {
        return downSince >= 0L
                && now - downSince < Math.max(0L, bleedoutMs - BLEEDOUT_GUARD_MS);
    }

    static boolean shouldCommitDead(long candidateSince, long now) {
        return candidateSince >= 0L && now - candidateSince >= DEAD_CONFIRM_MS;
    }
}

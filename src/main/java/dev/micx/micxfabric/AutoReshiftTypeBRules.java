package dev.micx.micxfabric;

/** Pure rules for the Type B rescue-only Shift guard. */
public final class AutoReshiftTypeBRules {

    public static final long ACTIONBAR_FRESH_MS = 750L;
    public static final long MISSING_ACTIONBAR_GRACE_MS = 300L;
    public static final float LOW_HEALTH_BYPASS_HP = 7.0f;
    public static final int DUO_START_ROUND = 36;
    public static final int NON_DUO_START_ROUND = 39;

    private AutoReshiftTypeBRules() { }

    /**
     * Returns whether a current local rescue snapshot is strong enough to arm the guard.
     * A HOLD prompt is deliberately excluded because rescue progress has not started yet.
     */
    public static boolean shouldGuard(boolean inAlienArcadium, boolean selfDown,
                                      boolean selfDead, String target, boolean targetDown,
                                      long modeMs, long observedAt, long now) {
        if (!inAlienArcadium || selfDown || selfDead) return false;
        if (target == null || target.trim().isEmpty() || !targetDown) return false;
        if (modeMs <= 0L) return false;
        long age = now - observedAt;
        return age >= 0L && age <= ACTIONBAR_FRESH_MS;
    }

    /** A guard may arm only while the player is physically holding the sneak binding. */
    public static boolean shouldArm(boolean inAlienArcadium, boolean selfDown,
                                    boolean selfDead, String target, boolean targetDown,
                                    long modeMs, long observedAt, long now,
                                    boolean sneakPhysicallyDown) {
        return sneakPhysicallyDown && shouldGuard(inAlienArcadium, selfDown, selfDead,
                target, targetDown, modeMs, observedAt, now);
    }

    /** Keep a previously armed guard briefly while the next actionbar update arrives. */
    public static boolean keepGuardDuringGrace(boolean wasActive, long lastEligibleAt, long now) {
        if (!wasActive || lastEligibleAt <= 0L) return false;
        long age = now - lastEligibleAt;
        return age >= 0L && age <= MISSING_ACTIONBAR_GRACE_MS;
    }

    /** Low health bypasses the guard below 3.5 hearts (7 raw HP). */
    public static boolean isLowHealth(float health) {
        return isLowHealth(health, LOW_HEALTH_BYPASS_HP);
    }

    /** Threshold-aware low health check; the panel can raise or lower the cutoff. */
    public static boolean isLowHealth(float health, float threshold) {
        return health < threshold;
    }

    /**
     * Duo ignores the low-health bypass entirely: in a duo every HP value may be guarded,
     * because letting go is what costs the last teammate the round.
     */
    public static boolean lowHealthBypass(float health, float threshold, boolean duo) {
        return !duo && isLowHealth(health, threshold);
    }

    /**
     * Round gate: the guard only starts working from a configurable round.
     * Duo starts earlier (default r36), trios/quads later (default r39).
     */
    public static boolean roundGate(int round, boolean duo,
                                    int duoStartRound, int nonDuoStartRound) {
        int need = duo ? duoStartRound : nonDuoStartRound;
        return round >= need;
    }

    /** The input layer may only restore a release while the guard is active. */
    public static ReleaseAction releaseAction(boolean guardActive, boolean guiOpen,
                                              boolean sneakRelease) {
        return releaseAction(guardActive, false, guiOpen, sneakRelease, false);
    }

    /** Low health must let the physical release pass through immediately. */
    public static ReleaseAction releaseAction(boolean guardActive, boolean guiOpen,
                                              boolean sneakRelease, boolean lowHealth) {
        return releaseAction(guardActive, false, guiOpen, sneakRelease, lowHealth);
    }

    /**
     * A release may be restored while the guard is active, or immediately when a fresh
     * rescue snapshot exists: the rescue progress is already server-confirmed, so even a
     * quick release (0.1-0.2s) must not be allowed to drop the rescue before the next tick.
     */
    public static ReleaseAction releaseAction(boolean guardActive, boolean rescueFresh,
                                              boolean guiOpen, boolean sneakRelease,
                                              boolean lowHealth) {
        return (guardActive || rescueFresh) && !guiOpen && sneakRelease && !lowHealth
                ? ReleaseAction.RESTORE_LOCAL_HOLD
                : ReleaseAction.PASS_THROUGH;
    }

    public enum ReleaseAction {
        PASS_THROUGH,
        RESTORE_LOCAL_HOLD
    }
}

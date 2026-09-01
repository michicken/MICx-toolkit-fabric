package dev.micx.micxfabric;

/** Pure timing and transition rules for the Zombies sidebar session lifecycle. */
final class ZombiesSessionRules {
    /** 侧栏缺失确认宽限。1s 太短：Hypixel 重建侧栏/瞬时判定失败会误判退出，导致重检出后清空回合计数。 */
    static final long NON_ZOMBIES_CONFIRM_MS = 3_000L;

    private ZombiesSessionRules() {
    }

    static boolean shouldConfirmExit(boolean sessionActive, long candidateSince, long now) {
        return sessionActive && candidateSince >= 0L
                && now - candidateSince >= NON_ZOMBIES_CONFIRM_MS;
    }

    static boolean isRoundRestart(int currentRound, int observedRound) {
        return currentRound > 1 && observedRound > 0 && observedRound < currentRound;
    }
}

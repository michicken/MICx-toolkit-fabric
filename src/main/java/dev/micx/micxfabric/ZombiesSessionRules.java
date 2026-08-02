package dev.micx.micxfabric;

/** Pure timing and transition rules for the Zombies sidebar session lifecycle. */
final class ZombiesSessionRules {
    static final long NON_ZOMBIES_CONFIRM_MS = 1_000L;

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

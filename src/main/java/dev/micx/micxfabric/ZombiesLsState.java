package dev.micx.micxfabric;

/** Minecraft-free LS visibility latch copied from the Forge round-state contract. */
final class ZombiesLsState {
    private int round;
    private int simultaneousDown;
    private int previousDown;
    private int downEvents;
    private boolean latched;

    void reset() {
        round = 0;
        simultaneousDown = 0;
        previousDown = 0;
        downEvents = 0;
        latched = false;
    }

    /** Observes one scoreboard snapshot; a round change clears the previous latch. */
    void observe(int observedRound, int downCount) {
        int safeDownCount = Math.max(0, downCount);
        if (observedRound != round) {
            round = observedRound;
            simultaneousDown = safeDownCount;
            previousDown = safeDownCount;
            downEvents = 0;
            latched = false;
            return;
        }
        simultaneousDown = safeDownCount;
        if (safeDownCount > previousDown) downEvents += safeDownCount - previousDown;
        previousDown = safeDownCount;
    }

    boolean shouldShow() {
        if (latched) return true;
        if (ZombiesRoundData.isLsRound(round)) {
            if (simultaneousDown >= 1 || downEvents >= 1) latched = true;
        } else if (round == 55 || round == 58) {
            if (simultaneousDown >= 2 || downEvents >= 3) latched = true;
        }
        return latched;
    }

    int round() {
        return round;
    }

    int simultaneousDown() {
        return simultaneousDown;
    }

    int downEvents() {
        return downEvents;
    }
}

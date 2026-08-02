package dev.micx.micxfabric;

/** Stable status priority for the LS teammate rows. */
final class ZombiesLsStatusRules {
    enum Status {
        DOWN,
        DEAD,
        LOW_HP,
        ALIVE,
        UNAVAILABLE
    }

    private ZombiesLsStatusRules() {
    }

    static Status classify(String scoreboardStatus, boolean playerPresent,
                           float healthWithAbsorption, float maxHealth) {
        if ("down".equals(scoreboardStatus)) return Status.DOWN;
        if ("dead".equals(scoreboardStatus) || "quit".equals(scoreboardStatus)) return Status.DEAD;
        if (!playerPresent) return Status.UNAVAILABLE;
        if (Float.isFinite(healthWithAbsorption) && Float.isFinite(maxHealth)
                && healthWithAbsorption < 10.0f) return Status.LOW_HP;
        return Status.ALIVE;
    }
}

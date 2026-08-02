package dev.micx.micxfabric;

/**
 * Pure tick-driven state machine for an abnormal reload. Raw item damage is
 * inverse to the visible durability bar, so values near max damage are suspect.
 */
final class JamProtectionTracker {
    static final float SUSPECT_DAMAGE_RATIO = 0.85f;
    static final float RECOVERED_DAMAGE_RATIO = 0.20f;
    static final long NORMAL_RECOVERY_WINDOW_MS = 5_000L;
    static final long SLOW_RECOVERY_WINDOW_MS = 3_000L;
    static final float SLOW_RECOVERY_MAX_RATIO = 0.06f;

    enum Event {
        NONE,
        WATCH_STARTED,
        NORMAL_RECOVERED,
        PRECURSOR_CONFIRMED,
        SLOW_RECOVERY_CONFIRMED
    }

    static final class Sample {
        final Event event;
        final long elapsedMs;
        final int startDamage;
        final int currentDamage;
        final int maxDamage;

        Sample(Event event, long elapsedMs, int startDamage, int currentDamage, int maxDamage) {
            this.event = event;
            this.elapsedMs = elapsedMs;
            this.startDamage = startDamage;
            this.currentDamage = currentDamage;
            this.maxDamage = maxDamage;
        }
    }

    private boolean watching;
    private boolean precursorConfirmed;
    private boolean slowRecoveryConfirmed;
    private long startedMs;
    private long precursorMs;
    private int startDamage;
    private int maxDamage;

    void reset() {
        watching = false;
        precursorConfirmed = false;
        slowRecoveryConfirmed = false;
        startedMs = 0L;
        precursorMs = 0L;
        startDamage = 0;
        maxDamage = 0;
    }

    Sample observe(int itemDamage, int currentMaxDamage, long now) {
        if (currentMaxDamage <= 0 || itemDamage < 0) {
            reset();
            return sample(Event.NONE, 0L, itemDamage, currentMaxDamage);
        }
        if (!watching) {
            if (itemDamage < threshold(currentMaxDamage, SUSPECT_DAMAGE_RATIO)) {
                return sample(Event.NONE, 0L, itemDamage, currentMaxDamage);
            }
            watching = true;
            startedMs = now;
            startDamage = itemDamage;
            maxDamage = currentMaxDamage;
            return sample(Event.WATCH_STARTED, 0L, itemDamage, currentMaxDamage);
        }
        if (currentMaxDamage != maxDamage) {
            reset();
            return observe(itemDamage, currentMaxDamage, now);
        }
        long elapsed = Math.max(0L, now - startedMs);
        if (itemDamage <= threshold(maxDamage, RECOVERED_DAMAGE_RATIO)) {
            Sample result = sample(Event.NORMAL_RECOVERED, elapsed, itemDamage, currentMaxDamage);
            reset();
            return result;
        }
        if (!precursorConfirmed && elapsed >= NORMAL_RECOVERY_WINDOW_MS) {
            precursorConfirmed = true;
            precursorMs = now;
            return sample(Event.PRECURSOR_CONFIRMED, elapsed, itemDamage, currentMaxDamage);
        }
        if (precursorConfirmed && !slowRecoveryConfirmed
                && now - precursorMs >= SLOW_RECOVERY_WINDOW_MS) {
            int recoveredRawDamage = Math.max(0, startDamage - itemDamage);
            int maxAllowed = Math.max(1, Math.round(maxDamage * SLOW_RECOVERY_MAX_RATIO));
            if (recoveredRawDamage <= maxAllowed) {
                slowRecoveryConfirmed = true;
                return sample(Event.SLOW_RECOVERY_CONFIRMED, elapsed, itemDamage, currentMaxDamage);
            }
        }
        return sample(Event.NONE, elapsed, itemDamage, currentMaxDamage);
    }

    private Sample sample(Event event, long elapsed, int currentDamage, int currentMaxDamage) {
        return new Sample(event, elapsed, startDamage, currentDamage, currentMaxDamage);
    }

    private static int threshold(int maxDamage, float ratio) {
        return Math.max(1, (int) Math.ceil(maxDamage * ratio));
    }
}

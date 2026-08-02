package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JamProtectionTrackerTest {
    @Test
    void normalReloadReturnsToLowRawDamageWithoutProtection() {
        JamProtectionTracker tracker = new JamProtectionTracker();
        assertEquals(JamProtectionTracker.Event.WATCH_STARTED, tracker.observe(236, 250, 0L).event);
        assertEquals(JamProtectionTracker.Event.NONE, tracker.observe(220, 250, 1_000L).event);
        assertEquals(JamProtectionTracker.Event.NORMAL_RECOVERED, tracker.observe(15, 250, 1_600L).event);
    }

    @Test
    void highRawDamageThatDoesNotRecoverBecomesPrecursor() {
        JamProtectionTracker tracker = new JamProtectionTracker();
        tracker.observe(236, 250, 0L);
        assertEquals(JamProtectionTracker.Event.PRECURSOR_CONFIRMED,
                tracker.observe(249, 250, 5_000L).event);
    }

    @Test
    void slowRecoveryAfterPrecursorConfirmsActualJam() {
        JamProtectionTracker tracker = new JamProtectionTracker();
        tracker.observe(1_520, 1_561, 0L);
        assertEquals(JamProtectionTracker.Event.PRECURSOR_CONFIRMED,
                tracker.observe(1_520, 1_561, 5_000L).event);
        assertEquals(JamProtectionTracker.Event.SLOW_RECOVERY_CONFIRMED,
                tracker.observe(1_490, 1_561, 8_000L).event);
    }

    @Test
    void replacingTheItemDefinitionResetsTheWatch() {
        JamProtectionTracker tracker = new JamProtectionTracker();
        tracker.observe(236, 250, 0L);
        assertEquals(JamProtectionTracker.Event.WATCH_STARTED,
                tracker.observe(900, 1_000, 100L).event);
    }
}

package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombiesLsStatusRulesTest {
    @Test
    void statusPriorityKeepsDownAndDeadAboveHealth() {
        assertEquals(ZombiesLsStatusRules.Status.DOWN,
                ZombiesLsStatusRules.classify("down", true, 1.0f, 20.0f));
        assertEquals(ZombiesLsStatusRules.Status.DEAD,
                ZombiesLsStatusRules.classify("dead", true, 20.0f, 20.0f));
        assertEquals(ZombiesLsStatusRules.Status.DEAD,
                ZombiesLsStatusRules.classify("quit", false, Float.NaN, Float.NaN));
    }

    @Test
    void missingPlayerDoesNotFabricateHealth() {
        assertEquals(ZombiesLsStatusRules.Status.UNAVAILABLE,
                ZombiesLsStatusRules.classify("alive", false, Float.NaN, Float.NaN));
        assertEquals(ZombiesLsStatusRules.Status.LOW_HP,
                ZombiesLsStatusRules.classify("alive", true, 9.9f, 20.0f));
        assertEquals(ZombiesLsStatusRules.Status.ALIVE,
                ZombiesLsStatusRules.classify("alive", true, 10.0f, 20.0f));
    }

    @Test
    void unknownScoreboardStatusStillUsesObservedPlayer() {
        assertEquals(ZombiesLsStatusRules.Status.ALIVE,
                ZombiesLsStatusRules.classify(null, true, 20.0f, 20.0f));
        assertFalse(ZombiesLsStatusRules.classify(null, false, Float.NaN, Float.NaN)
                == ZombiesLsStatusRules.Status.ALIVE);
        assertTrue(ZombiesLsStatusRules.classify("down", true, 20.0f, 20.0f)
                == ZombiesLsStatusRules.Status.DOWN);
    }
}

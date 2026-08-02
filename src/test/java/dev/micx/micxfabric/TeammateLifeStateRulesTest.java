package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeammateLifeStateRulesTest {
    @Test
    void aliveNeedsSidebarCatchupOrGraceAndTwoHundredMsConfirmation() {
        assertFalse(TeammateLifeStateRules.shouldClearDown(1_000L, false,
                1_100L, 1_299L));
        assertTrue(TeammateLifeStateRules.shouldClearDown(1_000L, true,
                1_100L, 1_300L));
        assertTrue(TeammateLifeStateRules.shouldClearDown(1_000L, false,
                1_800L, 2_000L));
    }

    @Test
    void freshDownIsProtectedUntilOneSecondBeforeBleedout() {
        assertTrue(TeammateLifeStateRules.protectsActiveDown(1_000L, 24_999L, 25_000L));
        assertFalse(TeammateLifeStateRules.protectsActiveDown(1_000L, 25_000L, 25_000L));
    }

    @Test
    void deadRequiresContinuousTwoHundredMsCandidate() {
        assertFalse(TeammateLifeStateRules.shouldCommitDead(2_000L, 2_199L));
        assertTrue(TeammateLifeStateRules.shouldCommitDead(2_000L, 2_200L));
    }
}

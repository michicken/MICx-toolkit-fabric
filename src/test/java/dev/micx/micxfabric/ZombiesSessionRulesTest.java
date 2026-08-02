package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombiesSessionRulesTest {
    @Test
    void transientSidebarLossDoesNotConfirmExitImmediately() {
        assertFalse(ZombiesSessionRules.shouldConfirmExit(true, 10_000L, 10_999L));
        assertTrue(ZombiesSessionRules.shouldConfirmExit(true, 10_000L, 11_000L));
        assertFalse(ZombiesSessionRules.shouldConfirmExit(false, 10_000L, 11_000L));
    }

    @Test
    void lowerRoundMarksAConfirmedRestartButRoundOneDoesNot() {
        assertTrue(ZombiesSessionRules.isRoundRestart(43, 1));
        assertTrue(ZombiesSessionRules.isRoundRestart(43, 42));
        assertFalse(ZombiesSessionRules.isRoundRestart(1, 1));
        assertFalse(ZombiesSessionRules.isRoundRestart(43, 0));
    }
}

package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChamsTargetRulesTest {
    @Test
    void includesOnlyConfiguredLivingTargetFamilies() {
        assertTrue(target(false, false, false, true, false, false, true, false));
        assertTrue(target(false, false, false, false, true, false, true, false));
        assertTrue(target(false, false, false, false, false, true, true, false));

        assertFalse(target(false, false, false, false, false, false, true, false));
        assertFalse(target(true, false, false, true, false, false, true, false));
        assertFalse(target(false, true, false, true, false, false, true, false));
        assertFalse(target(false, false, true, true, false, false, true, false));
    }

    @Test
    void excludesRemovedDeadAndDeathAnimationStates() {
        assertFalse(target(false, false, false, true, false, false, false, false));
        assertFalse(target(false, false, false, true, false, false, true, true));
    }

    private static boolean target(boolean wither, boolean player, boolean villager,
                                 boolean hostile, boolean wolf, boolean ironGolem,
                                 boolean alive, boolean deathAnimation) {
        return ChamsTargetRules.isTarget(wither, player, villager, hostile, wolf, ironGolem,
                alive, deathAnimation);
    }
}

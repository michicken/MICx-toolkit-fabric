package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JamProtectionRulesTest {
    @Test
    void recoveredGunSlotsIgnoreSwordAndNonWeaponChanges() {
        int[] before = {1, 4, 2, 1, 9, 3, 0, 0, 7};
        int[] current = {1, 1, 1, 1, 1, 1, 0, 0, 1};
        boolean[] weapons = {false, true, true, false, true, true, false, false, true};
        assertArrayEquals(new int[]{1, 2, 4, 5, 8},
                JamProtectionRules.findRecoveredGunSlots(before, current, weapons));
    }

    @Test
    void allEmptyRequiresNineHotbarCounts() {
        assertTrue(JamProtectionRules.allEmpty(new int[9]));
        assertFalse(JamProtectionRules.allEmpty(new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1}));
        assertFalse(JamProtectionRules.allEmpty(new int[8]));
    }

    @Test
    void specialRoundsMatchForgeTableAndHighRoundTail() {
        for (int round : new int[]{53, 54, 55, 58, 65, 69, 70, 85, 90, 99}) {
            assertTrue(JamProtectionRules.isSpecialRound(round), "round=" + round);
        }
        for (int round : new int[]{1, 52, 56, 80, 84, 86, 91}) {
            assertFalse(JamProtectionRules.isSpecialRound(round), "round=" + round);
        }
    }
}

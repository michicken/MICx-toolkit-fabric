package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombieThreatRulesTest {
    @Test
    void exactTooSignatureHonorsStrictThresholdsAndSlimeExclusion() {
        assertTrue(ZombieThreatRules.isToo(true, true, true,
                ZombieThreatRules.TOO_CHEST_COLOR, false));
        assertTrue(ZombieThreatRules.isToo(true, true, true,
                ZombieThreatRules.TOO_CHEST_COLOR + 499, false));
        assertFalse(ZombieThreatRules.isToo(true, true, true,
                ZombieThreatRules.TOO_CHEST_COLOR + 500, false));
        assertFalse(ZombieThreatRules.isToo(true, true, true,
                ZombieThreatRules.SLIME_BABY_COLOR, true));
        assertFalse(ZombieThreatRules.isToo(false, true, true,
                ZombieThreatRules.TOO_CHEST_COLOR, true));
    }

    @Test
    void greenFallbackIsEnabledByTooStrictGreenFlag() {
        int green = 0x00AA32;
        assertTrue(ZombieThreatRules.isToo(true, true, false, green, true));
        assertFalse(ZombieThreatRules.isToo(true, true, false,
                ZombieThreatRules.SLIME_BABY_COLOR, true));
        assertFalse(ZombieThreatRules.isToo(true, false, false, green, true));
    }

    @Test
    void clownUsesForgeYellowOrThreeDistinctArmorColors() {
        assertTrue(ZombieThreatRules.isClown(false,
                ZombieThreatRules.CLOWN_YELLOW, null, null));
        assertFalse(ZombieThreatRules.isClown(true,
                ZombieThreatRules.CLOWN_YELLOW, null, null));
        assertTrue(ZombieThreatRules.isClown(false, 0x101010, 0xFFFFFF, 0x000000));
        assertFalse(ZombieThreatRules.isClown(false, 0x101010, 0x202020, 0x202020));
    }

    @Test
    void giantRequiresExplicitEntityType() {
        assertTrue(ZombieThreatRules.isGiantEntityType(true));
        assertFalse(ZombieThreatRules.isGiantEntityType(false));
    }
}

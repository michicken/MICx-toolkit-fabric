package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmmoTrackerTest {
    @Test
    void recognizesForgeWeaponTagsAndReloadDurations() {
        assertEquals("DSG", AmmoTracker.gunTag("Double Barrel"));
        assertEquals("PT", AmmoTracker.gunTag("Ultimate Pistol"));
        assertEquals("SG", AmmoTracker.gunTag("Shotgun"));
        assertEquals("RR", AmmoTracker.gunTag("Rainbow Rifle"));
        assertEquals("GD", AmmoTracker.gunTag("Gold Digger"));
        assertEquals("ZP", AmmoTracker.gunTag("Zapper"));
        assertEquals("刀", AmmoTracker.gunTag("Knife"));
        assertEquals(2_000L, AmmoTracker.expectedReloadMs("ZP"));
        assertEquals(1_500L, AmmoTracker.expectedReloadMs("DSG"));
    }
}

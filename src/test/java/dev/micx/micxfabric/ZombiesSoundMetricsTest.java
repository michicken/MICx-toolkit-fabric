package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombiesSoundMetricsTest {
    @Test
    void alienArcadiumGateAndHitPitchRulesAreExact() {
        ZombiesSoundMetrics metrics = new ZombiesSoundMetrics();
        metrics.observe("minecraft:entity.lightning_bolt.thunder", 1.0f,
                0.0, 0.0, 0.0, 1_000L, false, List.of());
        assertEquals(0, metrics.lrUses());

        metrics.observe("minecraft:entity.lightning_bolt.thunder", 1.0f,
                0.0, 0.0, 0.0, 1_100L, true, List.of());
        metrics.observe("minecraft:entity.lightning_bolt.thunder", 2.0f,
                0.0, 0.0, 0.0, 1_200L, true, List.of());
        metrics.observe("minecraft:entity.player.attack.strong", 1.5f,
                0.0, 0.0, 0.0, 1_300L, true, List.of());
        metrics.observe("minecraft:entity.player.attack.strong", 2.0f,
                0.0, 0.0, 0.0, 1_400L, true, List.of());
        assertEquals(1, metrics.lrUses());
        assertEquals(2, metrics.hits());
        assertEquals(1, metrics.crits());
    }

    @Test
    void nearestPlayerReceivesWeaponAndFireWindowExpires() {
        ZombiesSoundMetrics metrics = new ZombiesSoundMetrics();
        List<ZombiesSoundMetrics.PlayerPoint> players = List.of(
                new ZombiesSoundMetrics.PlayerPoint("near", 1.0, 0.0, 0.0),
                new ZombiesSoundMetrics.PlayerPoint("far", 2.9, 0.0, 0.0));
        metrics.observe("minecraft:entity.iron_golem.attack", 2.5f,
                0.0, 0.0, 0.0, 2_000L, true, players);
        assertTrue(metrics.isFiring("near", 2_600L));
        assertEquals("Pistol", metrics.fireWeapon("near", 2_600L));
        assertFalse(metrics.isFiring("near", 2_601L));
        assertNull(metrics.fireWeapon("far", 2_100L));
    }

    @Test
    void weaponPitchBoundariesDoNotCrossClassify() {
        assertEquals("DBarrel", ZombiesSoundMetrics.weaponOf(
                "minecraft:entity.firework_rocket.large_blast", 0.8f));
        assertEquals("Rifle", ZombiesSoundMetrics.weaponOf(
                "minecraft:entity.firework_rocket.large_blast", 2.5f));
        assertNull(ZombiesSoundMetrics.weaponOf(
                "minecraft:entity.firework_rocket.large_blast", 1.4f));
    }
}

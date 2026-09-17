package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerUpTimerTest {
    @Test
    void dropsAreIdempotentAndExpireAtTheDeadline() {
        PowerUpTimer timer = new PowerUpTimer();
        timer.observeDrop("max", 7, 12, 1_000L);
        timer.observeDrop("max", 7, 99, 2_000L);

        assertEquals(1_000L, timer.dropsSnapshot().get(7).firstSeenAt());
        assertEquals(60_000L, timer.dropRemainingMs(7, 1_000L));

        timer.expire(60_999L);
        assertTrue(timer.dropsSnapshot().containsKey(7));
        timer.expire(61_000L);
        assertTrue(timer.dropsSnapshot().isEmpty());
    }

    @Test
    void repeatedActivationKeepsLongestRemaining() {
        PowerUpTimer timer = new PowerUpTimer();
        timer.activate("Insta Kill", 10, 1_000L);
        timer.activate("Insta Kill", 10, 2_000L);
        assertEquals(12_000L, timer.activeSnapshot().get("insta").expiresAt());
        timer.expire(11_999L);
        assertTrue(timer.activeSnapshot().containsKey("insta"));
        timer.expire(12_000L);
        assertTrue(timer.activeSnapshot().isEmpty());
    }

    @Test
    void samePowerUpFromBothSourcesKeepsASingleEntry() {
        // 聊天路径给 INSTA KILL、字幕路径给 Insta Kill —— 归一化后只留一条（1.8.9 同口径）
        PowerUpTimer timer = new PowerUpTimer();
        timer.activate("INSTA KILL", 10, 1_000L);
        timer.activate("Insta Kill", 10, 1_100L);
        assertEquals(1, timer.activeSnapshot().size());
        assertEquals(11_100L, timer.activeSnapshot().get("insta").expiresAt());
    }

    @Test
    void dualPowerUpsBothPresent() {
        PowerUpTimer timer = new PowerUpTimer();
        timer.activate("Insta Kill", 10, 1_000L);
        timer.activate("Double Gold", 30, 1_100L);
        // 两条同时存在（键已归一化）——顶部把两条都画出来由 PowerUpHudRules 负责
        assertEquals(11_000L, timer.activeSnapshot().get("insta").expiresAt());
        assertEquals(31_100L, timer.activeSnapshot().get("dg").expiresAt());
    }
}

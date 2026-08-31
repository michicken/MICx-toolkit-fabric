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
        assertEquals(12_000L, timer.activeSnapshot().get("Insta Kill").expiresAt());
        timer.expire(11_999L);
        assertTrue(timer.activeSnapshot().containsKey("Insta Kill"));
        timer.expire(12_000L);
        assertTrue(timer.activeSnapshot().isEmpty());
    }

    @Test
    void dualPowerUpsBothPresent() {
        PowerUpTimer timer = new PowerUpTimer();
        timer.activate("Insta Kill", 10, 1_000L);
        timer.activate("Double Gold", 30, 1_100L);
        assertEquals(11_000L, timer.activeSnapshot().get("Insta Kill").expiresAt());
        assertEquals(31_100L, timer.activeSnapshot().get("Double Gold").expiresAt());
        // 顶部只读最长那条由 ZombiesAssistModule.drawActivePowerUpsTop 决定
    }
}

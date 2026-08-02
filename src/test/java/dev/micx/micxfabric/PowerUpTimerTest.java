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
    void correlatedActivationDoesNotRestartTheTimer() {
        PowerUpTimer timer = new PowerUpTimer();
        timer.activate("Insta Kill", 10, 1_000L);
        timer.activate("Insta Kill", 10, 2_000L);

        assertEquals(11_000L, timer.activeSnapshot().get("Insta Kill").expiresAt());
        timer.expire(11_000L);
        assertTrue(timer.activeSnapshot().isEmpty());
    }
}

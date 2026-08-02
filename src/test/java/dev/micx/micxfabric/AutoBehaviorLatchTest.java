package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoBehaviorLatchTest {
    @Test
    void claimsEachRoundActionOnce() {
        AutoBehaviorLatch latch = new AutoBehaviorLatch();

        assertTrue(latch.claimRound("round-announcement", 43));
        assertFalse(latch.claimRound("round-announcement", 43));
        assertTrue(latch.claimRound("round-announcement", 44));
        assertTrue(latch.claimGameOver(43));
        assertFalse(latch.claimGameOver(43));
    }
}

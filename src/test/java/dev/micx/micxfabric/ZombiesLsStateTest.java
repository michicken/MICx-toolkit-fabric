package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombiesLsStateTest {
    @Test
    void lightningRoundLatchesAfterFirstDownUntilRoundChanges() {
        ZombiesLsState state = new ZombiesLsState();
        state.observe(70, 0);
        assertFalse(state.shouldShow());
        state.observe(70, 1);
        assertTrue(state.shouldShow());
        state.observe(70, 0);
        assertTrue(state.shouldShow());
        state.observe(71, 0);
        assertFalse(state.shouldShow());
    }

    @Test
    void riskRoundsLatchOnTwoSimultaneousDownsOrThreeDownEvents() {
        ZombiesLsState simultaneous = new ZombiesLsState();
        simultaneous.observe(55, 0);
        simultaneous.observe(55, 2);
        assertTrue(simultaneous.shouldShow());

        ZombiesLsState repeated = new ZombiesLsState();
        repeated.observe(58, 0);
        repeated.observe(58, 1);
        repeated.observe(58, 0);
        repeated.observe(58, 1);
        repeated.observe(58, 0);
        repeated.observe(58, 1);
        assertTrue(repeated.shouldShow());
    }

    @Test
    void resetClearsRoundAndLatch() {
        ZombiesLsState state = new ZombiesLsState();
        state.observe(90, 1);
        assertTrue(state.shouldShow());
        state.reset();
        assertFalse(state.shouldShow());
        assertTrue(state.round() == 0);
    }
}

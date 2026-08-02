package dev.micx.micxfabric;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerUpGroupStateTest {
    private final PowerUpGroupState state = PowerUpGroupState.instance();

    @AfterEach
    void clearState() {
        state.clear();
    }

    @Test
    void locksIndependentGroupsFromObservedDropRounds() {
        state.lock("max", 2);
        state.lock("insta", 3);
        state.lock("shopping", 5);

        assertEquals(2, state.group("max"));
        assertEquals(3, state.group("insta"));
        assertEquals(5, state.group("shopping"));
        assertEquals("R5", state.forecast("max", 3));
        assertEquals("R6", state.forecast("insta", 4));
        assertEquals("R15", state.forecast("shopping", 6));
    }

    @Test
    void unknownKindsAndRoundsDoNotCreateLocks() {
        state.lock("unknown", 2);
        state.lock("max", 1);
        assertEquals(0, state.group("unknown"));
        assertEquals(0, state.group("max"));
        assertEquals(null, state.forecast("unknown", 1));
    }

    @Test
    void unlockedForecastIsMarkedUncertainAndDropLocksIt() {
        assertEquals("R2?", state.forecast("max", 1));
        state.lock("max", 2);
        assertEquals("NOW", state.forecast("max", 2));
    }
}

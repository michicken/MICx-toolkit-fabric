package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombiesEventStateTest {
    @Test
    void duplicateKnockAndReviveAreCountedOnce() {
        ZombiesEventState state = new ZombiesEventState();
        state.accept(new ZombiesEventParser.Event(ZombiesEventParser.Kind.KNOCK,
                "alice", null, null, 0, -1, "alice was knocked down"), 1_000L);
        state.accept(new ZombiesEventParser.Event(ZombiesEventParser.Kind.KNOCK,
                "alice", null, null, 0, -1, "alice was knocked down"), 1_100L);
        assertEquals(1, state.totalDowns());

        state.accept(new ZombiesEventParser.Event(ZombiesEventParser.Kind.REVIVE,
                "alice", "bob", null, 0, -1, "bob revived alice"), 2_000L);
        state.accept(new ZombiesEventParser.Event(ZombiesEventParser.Kind.REVIVE,
                "alice", "bob", null, 0, -1, "bob revived alice"), 2_100L);
        assertEquals(1, state.totalRevives());
        assertEquals(5_000L, state.frCooldownRemainingMs("alice", 2_000L));
    }

    @Test
    void scoreboardBleedoutGuardPreservesFreshDownAndLocalProtection() {
        ZombiesEventState state = new ZombiesEventState();
        state.accept(new ZombiesEventParser.Event(ZombiesEventParser.Kind.KNOCK,
                "you", null, null, 0, -1, "you were knocked down"), 1_000L);
        state.accept(new ZombiesEventParser.Event(ZombiesEventParser.Kind.REVIVE,
                "you", "alice", null, 0, -1, "alice revived you"), 2_000L);
        assertEquals("alive", state.statuses().get("you"));
        assertTrue(state.reviveProtectionRemainingMs(2_000L) > 0L);

        state.accept(new ZombiesEventParser.Event(ZombiesEventParser.Kind.KNOCK,
                "bob", null, null, 0, -1, "bob was knocked down"), 3_000L);
        state.mergeScoreboardStatus("bob", "dead", 3_500L);
        assertEquals("down", state.statuses().get("bob"));
        state.mergeScoreboardStatus("bob", "dead", 28_001L);
        assertEquals("down", state.statuses().get("bob"));
        state.mergeScoreboardStatus("bob", "dead", 28_201L);
        assertEquals("dead", state.statuses().get("bob"));
    }

    @Test
    void gameOverIsAConsumableOneShot() {
        ZombiesEventState state = new ZombiesEventState();
        state.accept(new ZombiesEventParser.Event(ZombiesEventParser.Kind.GAME_OVER,
                null, null, null, 0, -1, "Game Over"), 1_000L);
        assertTrue(state.hasGameOverPending());
        assertTrue(state.consumeGameOver());
        assertFalse(state.consumeGameOver());
    }
}

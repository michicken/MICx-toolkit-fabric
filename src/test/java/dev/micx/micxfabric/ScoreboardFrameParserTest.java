package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardFrameParserTest {
    @Test
    void parsesRoundLeftMapAndTeamStatuses() {
        ScoreboardFrame frame = ScoreboardFrame.of("ZOMBIES", List.of(
                "Round: 43",
                "Zombies Left: 1,234",
                "alice: 4,200",
                "bob: DOWN",
                "carol: QUIT"
        ), List.of(
                "Round: 43",
                "Zombies Left: 1,234",
                "alice: 4,200",
                "☠ bob: DOWN",
                "carol: QUIT"
        ));

        assertTrue(frame.isZombies());
        assertEquals(43, frame.round());
        assertEquals(1234, frame.zombiesLeft());
        assertEquals(4200, frame.playerGold("alice"));
        assertEquals("alive", frame.playerStatus("alice"));
        assertEquals("down", frame.playerStatus("bob"));
        assertEquals("quit", frame.playerStatus("carol"));
    }

    @Test
    void recognizesAlienArcadiumAreaWithoutGuessingRound() {
        ScoreboardFrame frame = ScoreboardFrame.of("ZOMBIES", List.of(
                "Area: Ferris Wheel",
                "Round: ?"
        ), null);

        assertTrue(frame.isAlienArcadium());
        assertEquals(-1, frame.round());
    }
}

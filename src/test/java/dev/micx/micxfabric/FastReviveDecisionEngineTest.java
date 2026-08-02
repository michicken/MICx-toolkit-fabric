package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastReviveDecisionEngineTest {
    @Test
    void buildsReadyCoolingAndFastRevivingRows() {
        List<FastReviveDecisionEngine.Row> rows = FastReviveDecisionEngine.buildRows(List.of(
                new FastReviveDecisionEngine.PlayerInput("cooling", 1_200L),
                new FastReviveDecisionEngine.PlayerInput("ready", 0L),
                new FastReviveDecisionEngine.PlayerInput("fast", 0L, true, 300L, 300L)),
                new FastReviveDecisionEngine.Latency(0, 0, "test", false));
        assertEquals(3, rows.size());
        assertEquals(FastReviveDecisionEngine.Action.FAST_REVIVING, rows.get(0).action);
        assertEquals(FastReviveDecisionEngine.Action.READY, rows.get(1).action);
        assertEquals(FastReviveDecisionEngine.Action.COOLING, rows.get(2).action);
    }

    @Test
    void allReadyHidesOnlyAfterThreeSeconds() {
        FastReviveDecisionEngine.Visibility first =
                FastReviveDecisionEngine.updateVisibility(true, true, 0L, 1_000L);
        assertTrue(first.visible);
        FastReviveDecisionEngine.Visibility held =
                FastReviveDecisionEngine.updateVisibility(true, true, first.allReadySince, 3_999L);
        assertTrue(held.visible);
        FastReviveDecisionEngine.Visibility hidden =
                FastReviveDecisionEngine.updateVisibility(true, true, first.allReadySince, 4_000L);
        assertFalse(hidden.visible);
    }

    @Test
    void latencyAdvanceIsBounded() {
        assertEquals(325L, FastReviveDecisionEngine.safeAdvanceMs(
                new FastReviveDecisionEngine.Latency(400, 100, "test", true)));
        assertEquals(0L, FastReviveDecisionEngine.safeAdvanceMs(
                new FastReviveDecisionEngine.Latency(400, 100, "test", false)));
    }

    @Test
    void jitterCanRemoveTheEntireSafeAdvance() {
        assertEquals(0L, FastReviveDecisionEngine.safeAdvanceMs(
                new FastReviveDecisionEngine.Latency(120, 200, "game", true)));
        assertEquals(600L, FastReviveDecisionEngine.safeAdvanceMs(
                new FastReviveDecisionEngine.Latency(2_000, 0, "game", true)));
    }
}

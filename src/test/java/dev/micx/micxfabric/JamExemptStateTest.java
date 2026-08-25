package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JamExemptStateTest {

    @Test
    public void exemptRoundsMatchForge() {
        for (int r : new int[]{59, 70, 80, 90, 100, 101}) {
            assertTrue(JamExemptState.isExemptRound(r), "round " + r);
        }
        assertFalse(JamExemptState.isExemptRound(58));
        assertFalse(JamExemptState.isExemptRound(60));
        assertFalse(JamExemptState.isExemptRound(1));
    }

    @Test
    public void shouldExemptRequiresThreatAndRound() {
        assertTrue(JamExemptState.shouldExempt(70, true));
        assertFalse(JamExemptState.shouldExempt(70, false));
        assertFalse(JamExemptState.shouldExempt(69, true));
    }

    @Test
    public void enterAndExitEmitSingleEvents() {
        JamExemptState s = new JamExemptState();
        assertEquals(JamExemptState.Event.NONE, s.observe(70, false));
        assertEquals(JamExemptState.Event.ENTERED, s.observe(70, true));
        assertEquals(JamExemptState.Event.NONE, s.observe(70, true));
        assertEquals(70, s.exemptRound());
        assertEquals(JamExemptState.Event.EXITED, s.observe(70, false));
        assertEquals(JamExemptState.Event.NONE, s.observe(70, false));
    }

    @Test
    public void roundIncrementAfterExemptCountsAsExit() {
        JamExemptState s = new JamExemptState();
        s.observe(59, true);
        // 威胁仍在但回合推进到非豁免回合 → 恢复（EXITED）
        assertEquals(JamExemptState.Event.EXITED, s.observe(60, true));
        assertEquals(0, s.exemptRound());
        assertEquals(59, s.lastExemptRound());
    }

    @Test
    public void consecutiveExemptRoundsStayExempt() {
        JamExemptState s = new JamExemptState();
        assertEquals(JamExemptState.Event.ENTERED, s.observe(100, true));
        // r100 → r101 连续豁免回合：保持豁免，仅更新回合号
        assertEquals(JamExemptState.Event.NONE, s.observe(101, true));
        assertTrue(s.isExempt());
        assertEquals(101, s.exemptRound());
    }

    @Test
    public void roundFallbackResetsSilently() {
        JamExemptState s = new JamExemptState();
        s.observe(101, true);
        // 新对局 round 回落 → 静默复位，不发 EXITED；lastExemptRound 保留（Forge 同语义）
        assertEquals(JamExemptState.Event.NONE, s.observe(3, false));
        assertFalse(s.isExempt());
        assertEquals(101, s.lastExemptRound());
    }

    @Test
    public void resetClearsEverything() {
        JamExemptState s = new JamExemptState();
        s.observe(59, true);
        assertTrue(s.isExempt());
        s.reset();
        assertFalse(s.isExempt());
        assertEquals(0, s.exemptRound());
        assertEquals(0, s.lastExemptRound());
    }
}

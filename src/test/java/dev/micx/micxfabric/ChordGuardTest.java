package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ChordGuard：组合键成员单键「按下挂起、松开触发、组合键按齐作废」（用户定稿 2026-09-22）。 */
class ChordGuardTest {

    private static final List<int[]> CHORDS = List.of(new int[]{86, 66}); // V+B

    @Test
    void membershipCoversAnyChordSlot() {
        assertTrue(ChordGuard.isMember(86, CHORDS));
        assertTrue(ChordGuard.isMember(66, CHORDS));
        assertFalse(ChordGuard.isMember(67, CHORDS));
        assertFalse(ChordGuard.isMember(86, List.of()));
        assertFalse(ChordGuard.isMember(86, null));
        assertFalse(ChordGuard.isMember(0, CHORDS));
        // 0 是组合键空位，不算成员键
        assertFalse(ChordGuard.isMember(86, List.of(new int[]{0, 0, 0})));
    }

    @Test
    void nonMemberKeysFireOnPressExactlyAsBefore() {
        assertEquals(ChordGuard.Step.FIRE_NOW,
                ChordGuard.advanceSingle(false, true, false, false, null));
        assertEquals(ChordGuard.Step.NONE,
                ChordGuard.advanceSingle(false, true, false, true, null));
        assertEquals(ChordGuard.Step.NONE,
                ChordGuard.advanceSingle(false, false, false, true, null));
    }

    @Test
    void memberKeysPendOnPressAndFireOnCleanRelease() {
        // 按下 V（组合键 V+B 还没按齐）：挂起，不立即触发
        assertEquals(ChordGuard.Step.ARM_PENDING,
                ChordGuard.advanceSingle(true, true, false, false, null));
        // 按住期间无动作（等组合键按齐或等松开）
        assertEquals(ChordGuard.Step.NONE,
                ChordGuard.advanceSingle(true, true, false, true, ChordGuard.Phase.PENDING));
        // 组合键没按齐、松开 → 单键此刻才触发
        assertEquals(ChordGuard.Step.FIRE_ON_RELEASE,
                ChordGuard.advanceSingle(true, false, false, true, ChordGuard.Phase.PENDING));
        // 组合键按齐过（SUPPRESSED）→ 松开不触发
        assertEquals(ChordGuard.Step.NONE,
                ChordGuard.advanceSingle(true, false, false, true, ChordGuard.Phase.SUPPRESSED));
        // 无挂起状态下松开：不触发
        assertEquals(ChordGuard.Step.NONE,
                ChordGuard.advanceSingle(true, false, false, true, null));
    }

    @Test
    void blockedPressesNeverFireAndOnlyConsumeTheEdge() {
        assertEquals(ChordGuard.Step.NONE,
                ChordGuard.advanceSingle(false, true, true, false, null));
        assertEquals(ChordGuard.Step.NONE,
                ChordGuard.advanceSingle(true, true, true, false, null));
        // GUI 里松开：挂起单键作废，不触发
        assertEquals(ChordGuard.Step.NONE,
                ChordGuard.advanceSingle(true, false, true, true, ChordGuard.Phase.PENDING));
    }
}

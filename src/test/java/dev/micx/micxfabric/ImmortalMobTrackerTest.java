package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 无敌怪追踪口径：某怪在同一局内持续存在满 2 个回合（第 N 回合首见、第 N+1 回合
 * 仍在场）即判无敌；回合未知不判；回合回退（新局）重置该 id 的记录。
 */
class ImmortalMobTrackerTest {

    @Test
    void firstSightingAndSameRoundAreNotImmortal() {
        ImmortalMobTracker tracker = new ImmortalMobTracker();
        assertFalse(tracker.isImmortal(42, 15));
        assertFalse(tracker.isImmortal(42, 15));
        assertEquals(1, tracker.trackedCount());
    }

    @Test
    void survivingIntoTheNextRoundMarksImmortal() {
        ImmortalMobTracker tracker = new ImmortalMobTracker();
        assertFalse(tracker.isImmortal(42, 15));   // 首见 R15
        // 存在满 2 个回合：R16 仍在场 → 判无敌
        assertTrue(tracker.isImmortal(42, 16));
        assertTrue(tracker.isImmortal(42, 17));
        // 不同 id 互不影响
        assertFalse(tracker.isImmortal(43, 17));
    }

    @Test
    void judgementOnlyNeedsTheRoundDifferenceNotEveryRound() {
        // 判定只依赖「首见回合 ↔ 当前回合」之差，不要求每回合都被调用过
        ImmortalMobTracker tracker = new ImmortalMobTracker();
        assertFalse(tracker.isImmortal(7, 20));   // 首见 R20
        assertTrue(tracker.isImmortal(7, 22));    // 直接跳到 R22（差 2）→ 无敌
    }

    @Test
    void unknownRoundNeitherJudgesNorRecords() {
        ImmortalMobTracker tracker = new ImmortalMobTracker();
        assertFalse(tracker.isImmortal(42, 0));
        assertFalse(tracker.isImmortal(42, -1));
        assertEquals(0, tracker.trackedCount());
        // 之后从真实回合开始追踪，不能因为未知回合调用过而立即判无敌
        assertFalse(tracker.isImmortal(42, 30));
    }

    @Test
    void roundRewindResetsThatIdToTheNewRound() {
        ImmortalMobTracker tracker = new ImmortalMobTracker();
        assertFalse(tracker.isImmortal(42, 15));
        assertTrue(tracker.isImmortal(42, 16));
        // 新局重开：同 id 在第一回合出现 → 重新计时，不再无敌
        assertFalse(tracker.isImmortal(42, 1));
        assertTrue(tracker.isImmortal(42, 2));
    }

    @Test
    void resetClearsAllTracking() {
        ImmortalMobTracker tracker = new ImmortalMobTracker();
        assertFalse(tracker.isImmortal(1, 5));
        assertFalse(tracker.isImmortal(2, 5));
        tracker.reset();
        assertEquals(0, tracker.trackedCount());
        assertFalse(tracker.isImmortal(1, 6));
    }
}

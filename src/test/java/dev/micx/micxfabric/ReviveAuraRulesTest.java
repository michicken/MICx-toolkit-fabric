package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviveAuraRulesTest {
    @Test
    void intervalGatesEverySend() {
        // 首次（lastMs = 0）立刻可发
        assertTrue(ReviveAuraRules.intervalReady(1_000L, 0L, 200.0));
        assertFalse(ReviveAuraRules.intervalReady(1_100L, 1_000L, 200.0));
        assertFalse(ReviveAuraRules.intervalReady(1_199L, 1_000L, 200.0));
        assertTrue(ReviveAuraRules.intervalReady(1_200L, 1_000L, 200.0));
        assertTrue(ReviveAuraRules.intervalReady(1_500L, 1_000L, 200.0));
        // 用户配置里的 100ms 也照常生效
        assertTrue(ReviveAuraRules.intervalReady(1_100L, 1_000L, 100.0));
        assertFalse(ReviveAuraRules.intervalReady(1_099L, 1_000L, 100.0));
    }

    @Test
    void intervalIsClampedToThePanelRange() {
        assertEquals(200.0, ReviveAuraRules.DEFAULT_INTERVAL_MS);
        assertEquals(50.0, ReviveAuraRules.clampInterval(0.0));
        assertEquals(50.0, ReviveAuraRules.clampInterval(-999.0));
        assertEquals(100.0, ReviveAuraRules.clampInterval(100.0));
        assertEquals(1000.0, ReviveAuraRules.clampInterval(5_000.0));
    }

    @Test
    void rotationNeverHitsTheSameTargetTwiceInARowWhenAnotherIsAvailable() {
        // 用户 2026-09-16 的场景：两个 Sleeping 在范围内 → 先点 1 号，间隔到了点 2 号
        int[] ids = {101, 202};
        double[] distances = {2.0, 3.0};
        int first = ReviveAuraRules.chooseIndex(ids, distances, -1);
        assertEquals(0, first, "没点过任何人时取最近的一只");
        int second = ReviveAuraRules.chooseIndex(ids, distances, ids[first]);
        assertEquals(1, second, "点上过 1 号之后要换 2 号，哪怕 1 号更近");
        // 轮换是持续的：再下一发又回到 1 号
        assertEquals(0, ReviveAuraRules.chooseIndex(ids, distances, ids[second]));
    }

    @Test
    void fallsBackToTheOnlyCandidate() {
        int[] only = {101};
        assertEquals(0, ReviveAuraRules.chooseIndex(only, new double[]{2.0}, 101));
        assertEquals(0, ReviveAuraRules.chooseIndex(only, new double[]{2.0}, -1));
        // 上一次点的已经起来了 → 剩余候选里挑最近的
        int[] rest = {202, 303};
        assertEquals(0, ReviveAuraRules.chooseIndex(rest, new double[]{2.5, 4.0}, 101));
        assertEquals(1, ReviveAuraRules.chooseIndex(rest, new double[]{2.5, 4.0}, 202));
    }

    @Test
    void emptyOrMismatchedInputHasNoPick() {
        assertEquals(-1, ReviveAuraRules.chooseIndex(new int[0], new double[0], -1));
        assertEquals(-1, ReviveAuraRules.chooseIndex(null, null, -1));
        assertEquals(-1, ReviveAuraRules.chooseIndex(new int[]{1, 2}, new double[]{1.0}, -1));
    }
}

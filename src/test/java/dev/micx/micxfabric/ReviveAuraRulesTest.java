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
    void freshTargetIsNeverBlockedByAnotherTargetsCooldown() {
        // 用户 0.2.110 的场景：给 A（下标 0）刚发过包、A 在冷却，B 从没点过 → 立刻选 B。
        double[] distances = {2.0, 3.0};
        long[] lastSent = {1_000L, 0L};
        assertEquals(1, ReviveAuraRules.nextSendIndex(distances, lastSent, 1_050L, 100.0),
                "A 冷却中（才 50ms），没点过的 B 不受牵连");
        // 反过来 B 在冷却、A 没点过也同理
        assertEquals(0, ReviveAuraRules.nextSendIndex(distances, new long[]{0L, 1_000L}, 1_050L, 100.0));
    }

    @Test
    void picksNearestAmongReadyTargets() {
        double[] distances = {4.0, 2.0, 3.0};
        long[] lastSent = {0L, 0L, 0L};
        assertEquals(1, ReviveAuraRules.nextSendIndex(distances, lastSent, 5_000L, 200.0));
        // 最近那只冷却中 → 退而求其次取可发里最近的
        long[] cooled = {0L, 4_900L, 0L};
        assertEquals(2, ReviveAuraRules.nextSendIndex(distances, cooled, 5_000L, 200.0));
    }

    @Test
    void sameTargetRespectsItsOwnCooldown() {
        double[] distances = {2.0};
        long[] lastSent = {1_000L};
        assertEquals(-1, ReviveAuraRules.nextSendIndex(distances, lastSent, 1_050L, 100.0),
                "只剩 A 且 A 冷却未到 → 本 tick 不发");
        assertEquals(-1, ReviveAuraRules.nextSendIndex(distances, lastSent, 1_099L, 100.0),
                "99ms < 100ms 冷却未到");
        assertEquals(0, ReviveAuraRules.nextSendIndex(distances, lastSent, 1_100L, 100.0));
    }

    @Test
    void pingPongAandBNeedsOnlyOneTickGapNotTwoIntervals() {
        // 旧全局节流：A→B 要等满 intervalMs。分目标后：发 A（t=1000），t=1050 即可发 B。
        double[] distances = {2.0, 3.0};
        long[] lastSent = {1_000L, 0L};
        assertEquals(1, ReviveAuraRules.nextSendIndex(distances, lastSent, 1_050L, 100.0));
        // 发完 B（t=1050）后若 A 又出现（倒地标记被抖清）：A 的冷却 t=1100 才到，此时都不可发。
        long[] bothSent = {1_000L, 1_050L};
        assertEquals(-1, ReviveAuraRules.nextSendIndex(distances, bothSent, 1_090L, 100.0));
        assertEquals(0, ReviveAuraRules.nextSendIndex(distances, bothSent, 1_100L, 100.0));
    }

    @Test
    void emptyOrMismatchedInputHasNoPick() {
        assertEquals(-1, ReviveAuraRules.nextSendIndex(new double[0], new long[0], 5_000L, 100.0));
        assertEquals(-1, ReviveAuraRules.nextSendIndex(null, null, 5_000L, 100.0));
        assertEquals(-1, ReviveAuraRules.nextSendIndex(new double[]{1.0, 2.0}, new long[]{0L}, 5_000L, 100.0));
    }
}

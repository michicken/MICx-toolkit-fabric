package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v0.2.11：Forge aimlead/AimLeadRoundRulesTest 用例全量移植（JUnit4 → JUnit5）。
 * 任何断言变更都会偏离 Forge 体感基准，禁止“顺手优化”数值。
 */
class AimLeadRoundRulesTest {

    @Test
    void aimLeadStaysActiveThroughRound25AndStopsAfterwards() {
        assertTrue(AimLeadRoundRules.activeForRound(0));
        for (int round = 1; round <= 25; round++) {
            assertTrue(AimLeadRoundRules.activeForRound(round), "R" + round);
        }
        assertFalse(AimLeadRoundRules.activeForRound(26));
        assertFalse(AimLeadRoundRules.activeForRound(105));
    }

    @Test
    void newRoundOneIsAcceptedOnlyAfterPreviousLateRound() {
        assertFalse(AimLeadRoundRules.isNewGameRound(24, 1));
        assertTrue(AimLeadRoundRules.isNewGameRound(25, 1));
        assertTrue(AimLeadRoundRules.isNewGameRound(105, 1));
        assertFalse(AimLeadRoundRules.isNewGameRound(25, 2));
    }

    @Test
    void stoppedTargetUsesShortWindowAndFastReturn() {
        assertTrue(AimLeadRoundRules.isStopped(160L, 0.099, 0.05));
        assertFalse(AimLeadRoundRules.isStopped(159L, 0.01, 0.05));
        assertFalse(AimLeadRoundRules.isStopped(160L, 0.10, 0.05));
        // 窗口位移小但中位数速度持续 → 不算停（缓慢移动怪）
        assertFalse(AimLeadRoundRules.isStopped(160L, 0.05, 0.4));
        assertTrue(AimLeadRoundRules.RETURN_SMOOTH > AimLeadRoundRules.MOVING_SMOOTH);
    }

    @Test
    void verticalFallIsNotStopped() {
        // 垂直下坠的怪水平不动但垂直在动，不算急停 —— 否则垂直无预判。
        assertFalse(AimLeadRoundRules.isStopped(160L, 0.64, 3.9));
        assertFalse(AimLeadRoundRules.isStopped(160L, 0.24, 1.5));
        // "鸡式飘落"怪（~0.4 m/s）窗口净位移 <0.1 但中位数速度持续 → 不算停
        assertFalse(AimLeadRoundRules.isStopped(200L, 0.08, 0.42));
    }

    @Test
    void velocityPairRejectionKeepsFalling() {
        // 自由落体/击退回落（vy<0）：永不剔除，哪怕 60 m/s。
        assertFalse(AimLeadRoundRules.rejectVelocityPair(0, -9, 0));
        assertFalse(AimLeadRoundRules.rejectVelocityPair(0, -60, 0));
        assertFalse(AimLeadRoundRules.rejectVelocityPair(3, -20, 4));     // 下落+水平混合
        // 上抛击退/向上瞬移（vy>8）：剔除
        assertTrue(AimLeadRoundRules.rejectVelocityPair(0, 10, 0));
        // 向下瞬移毛刺（超终端速度 ~78）：剔除
        assertTrue(AimLeadRoundRules.rejectVelocityPair(0, -90, 0));
        // 水平击退/传送（>8）：剔除
        assertTrue(AimLeadRoundRules.rejectVelocityPair(9, 0, 0));
        assertTrue(AimLeadRoundRules.rejectVelocityPair(6, 0, 7));
        // 正常行走/跳跃级运动：保留
        assertFalse(AimLeadRoundRules.rejectVelocityPair(2, 0, 2));
        assertFalse(AimLeadRoundRules.rejectVelocityPair(0, 7.9, 0));     // 上抛边界内
    }

    @Test
    void verticalVelocityUsesLatestShortWindow() {
        // 纯下落：全部为负 → 短窗中位数为负（下落速度）
        double[] falling = {-3.9, -3.8, -3.5, -3.2};
        assertTrue(AimLeadRoundRules.verticalVelocity(falling, 4) < -3.0);

        // 上抛(+) + 下落(−) 混合：全窗中位数被抹平 ≈0（旧逻辑 → 垂直零预判），
        // 短窗取最新 3 对（pvy 前 3 个 = 最新）→ 仍为下落负值 —— 核心修复。
        double[] mixed = {-3.9, -3.6, -3.0, 2.0, 4.5, 5.0};   // 前 3 对下落，后 3 对是旧的上抛段
        double vy = AimLeadRoundRules.verticalVelocity(mixed, 6);
        assertTrue(vy < -3.0, "混合轨迹短窗应取最新下落段，实际=" + vy);

        // 单对击退尖峰混入最新窗：中位数抗噪
        double[] spike = {-3.9, -7.5, -3.6, -3.0};
        assertTrue(AimLeadRoundRules.verticalVelocity(spike, 4) < -3.0);

        // pairs == 0 → 0（无样本）
        assertEquals(0.0, AimLeadRoundRules.verticalVelocity(new double[0], 0), 1e-9);
    }

    @Test
    void verticalMovingTargetNeverHoldsBack() {
        // 垂直运动显著（|vy| ≥ 1 m/s）→ 即使水平 lead = 0 也强制外推
        assertFalse(AimLeadRoundRules.shouldHoldBack(false, false, -3.9, 0.0, 1.36, false));
        assertFalse(AimLeadRoundRules.shouldHoldBack(false, false, 4.5, 0.0, 1.5, true));
        assertFalse(AimLeadRoundRules.shouldHoldBack(false, false, -1.0, 0.0, 0.35, false));   // 阈值边界
        // 垂直不显著 + 水平 lead 不足 → 收缩（静止怪/微小抖动）
        assertTrue(AimLeadRoundRules.shouldHoldBack(false, false, 0.2, 0.05, 0.0, false));
        assertTrue(AimLeadRoundRules.shouldHoldBack(false, false, -0.9, 0.14, 0.0, true));    // HIDE 0.15 以下
        // 水平 lead 充足 → 不收缩（正常移动怪）
        assertFalse(AimLeadRoundRules.shouldHoldBack(false, false, 0.1, 0.31, 0.0, false));
        // 停更 / 急停 → 无论垂直都收缩
        assertTrue(AimLeadRoundRules.shouldHoldBack(true, false, -3.9, 1.0, 1.36, false));
        assertTrue(AimLeadRoundRules.shouldHoldBack(false, true, -3.9, 1.0, 1.36, false));
    }

    @Test
    void slowFallingTargetShowsGhostByVerticalLead() {
        // "鸡式飘落"（vy≈-0.4~-0.6 m/s，τ≈0.35s）：垂直 lead ≥ 0.10 → 首次就显示
        assertFalse(AimLeadRoundRules.shouldHoldBack(false, false, -0.5, 0.10, 0.175, false));
        assertFalse(AimLeadRoundRules.shouldHoldBack(false, false, -0.4, 0.05, 0.14, false));
        // 显示后滞回：垂直 lead ≥ 0.04 保持显示
        assertFalse(AimLeadRoundRules.shouldHoldBack(false, false, -0.3, 0.05, 0.105, true));
        // 垂直 lead 不足 0.10（vy 过小）→ 按水平 lead 判（收缩）
        assertTrue(AimLeadRoundRules.shouldHoldBack(false, false, -0.2, 0.05, 0.07, false));
    }

    @Test
    void serverStaleRequiresAlsoSlowSpeed() {
        // 停更超 250ms + 速度也小 = 真停 → 收缩
        assertTrue(AimLeadRoundRules.isServerStale(300L, 0.05));
        assertTrue(AimLeadRoundRules.isServerStale(251L, 0.1));
        // 未超时 → 不收缩
        assertFalse(AimLeadRoundRules.isServerStale(249L, 0.0));
        // 停更但速度持续（慢速怪广播间隔大）→ 仍在动，不收缩
        assertFalse(AimLeadRoundRules.isServerStale(300L, 0.42));
        assertFalse(AimLeadRoundRules.isServerStale(500L, 0.35));
        // 阈值边界：速度 ≥ 0.3 不算停更
        assertFalse(AimLeadRoundRules.isServerStale(300L, 0.3));
        assertTrue(AimLeadRoundRules.isServerStale(300L, 0.29));
    }

    @Test
    void groundClampNeededOnlyWhenPredictionBelowFeet() {
        // 脚底 10.5（站在 y=10 方块上，表面 11.0）：
        assertFalse(AimLeadRoundRules.groundClampNeeded(11.05, 10.5));
        assertFalse(AimLeadRoundRules.groundClampNeeded(11.0, 10.5));    // 恰在表面 → 不查
        assertTrue(AimLeadRoundRules.groundClampNeeded(10.99, 10.5));    // 低于表面 → 查
        assertTrue(AimLeadRoundRules.groundClampNeeded(8.0, 10.5));      // 深穿地 → 查
        // 整数脚底 11.0（地面 y=11）：表面 12.0
        assertFalse(AimLeadRoundRules.groundClampNeeded(12.0, 11.0));
        assertTrue(AimLeadRoundRules.groundClampNeeded(11.5, 11.0));
        // 下落怪（预测点远低于脚底）→ 查
        assertTrue(AimLeadRoundRules.groundClampNeeded(3.0, 10.5));
    }
}

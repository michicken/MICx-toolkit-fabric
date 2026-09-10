package dev.micx.micxfabric;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimbotRulesTest {
    private static AimbotRules.Candidate candidate(boolean threat, boolean too, boolean giant,
                                                    boolean head, int penetration, double angle) {
        return new AimbotRules.Candidate(threat, too, giant, head, penetration, angle);
    }

    @Test
    void angleDeltaWrapsAround() {
        assertEquals(0.0, AimbotRules.angleDelta(0, 0), 1.0e-9);
        assertEquals(20.0, AimbotRules.angleDelta(350, 10), 1.0e-9);
        assertEquals(-20.0, AimbotRules.angleDelta(10, 350), 1.0e-9);
        assertEquals(90.0, AimbotRules.angleDelta(270, 0), 1.0e-9);
        assertEquals(-90.0, AimbotRules.angleDelta(0, 270), 1.0e-9);
        assertEquals(179.0, AimbotRules.angleDelta(0, 179), 1.0e-9);
        assertEquals(-179.0, AimbotRules.angleDelta(0, -179), 1.0e-9);
    }

    @Test
    void headLayerIsTopFifth() {
        assertEquals(1.6, AimbotRules.headLayerBottom(2.0), 1.0e-9);
        assertTrue(AimbotRules.isHeadLayer(1.6, 0.0, 2.0));
        assertTrue(AimbotRules.isHeadLayer(1.9, 0.0, 2.0));
        assertFalse(AimbotRules.isHeadLayer(1.59, 0.0, 2.0));
    }

    @Test
    void lookVectorMatchesMinecraftYawPitchConvention() {
        Vec3 forward = AimbotRules.lookFromAngles(0.0f, 0.0f);
        assertEquals(0.0, forward.x, 1.0e-9);
        assertEquals(0.0, forward.y, 1.0e-9);
        assertEquals(1.0, forward.z, 1.0e-9);

        Vec3 west = AimbotRules.lookFromAngles(90.0f, 0.0f);
        assertEquals(-1.0, west.x, 1.0e-9);
        assertEquals(0.0, west.z, 1.0e-9);

        Vec3 up = AimbotRules.lookFromAngles(0.0f, -90.0f);
        assertEquals(1.0, up.y, 1.0e-9);

        Vec3 mixed = AimbotRules.lookFromAngles(37.0f, -23.0f);
        assertEquals(1.0, mixed.length(), 1.0e-9);
    }

    @Test
    void onlySingleOakSlabIsAddedToThePenetrableWhitelist() {
        assertTrue(AimbotRules.isAllowedSingleSlab("oak_slab", false));
        assertFalse(AimbotRules.isAllowedSingleSlab("oak_slab", true));
        assertFalse(AimbotRules.isAllowedSingleSlab("double_oak_slab", false));
        assertFalse(AimbotRules.isAllowedSingleSlab("spruce_slab", false));
        assertTrue(AimbotRules.isAllowedSingleSlab("stone_brick_slab", false));
        assertFalse(AimbotRules.isAllowedSingleSlab("stone_brick_slab", true));
    }

    @Test
    void normalPitchUsesAnAboveHorizonReserveAndHoldsCompatibleManualPitch() {
        assertEquals(-13.0, AimbotRules.normalPitchTarget(-10.0, 3.0), 1.0e-9);
        assertEquals(-4.0, AimbotRules.normalPitchTarget(-1.0, 3.0), 1.0e-9);
        assertEquals(2.0, AimbotRules.normalPitchTarget(2.0, 3.0), 1.0e-9);
        assertEquals(-1.0, AimbotRules.normalPitchTargetWithFallback(-1.0, 3.0, false), 1.0e-9);
        assertEquals(-4.0, AimbotRules.normalPitchTargetWithFallback(-1.0, 3.0, true), 1.0e-9);
        assertEquals(2.0, AimbotRules.normalPitchTargetWithFallback(2.0, 3.0, false), 1.0e-9);
        assertTrue(AimbotRules.normalPitchCompatible(-11.0, -13.0, 2.0));
        assertFalse(AimbotRules.normalPitchCompatible(-10.0, -13.0, 2.0));
    }

    @Test
    void giantAimPointKeepsTheCritBand() {
        // 原版巨人碰撞箱 3.6 × 12.0：90% 落点(10.8) 仍落在头部层 [9.6, 12.0] 内
        assertTrue(AimbotRules.isHeadLayer(10.8, 0.0, 12.0));
        assertFalse(AimbotRules.isHeadLayer(9.0, 0.0, 12.0));
        // 落点距爆头层上下边界各 1.2 格 → 爆头带容差为正且随距离收紧
        double near = AimbotRules.critPitchToleranceDeg(10.8, 0.0, 12.0, 10.0);
        double far = AimbotRules.critPitchToleranceDeg(10.8, 0.0, 12.0, 40.0);
        assertTrue(near > far);
        assertTrue(far >= 0.2);
    }

    @Test
    void bruteSweepGatesByRoundAndStaysInsideTheFov() {
        // 起始回合 53：之前不生效，53 起生效，未知回合不门控
        assertFalse(AimbotRules.bruteSweepAllowed(52, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(53, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(80, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(0, 53));
        assertTrue(AimbotRules.bruteSweepAllowed(-1, 53));
        // FOV 半角之内才参与扫射，不做 360° 乱扫
        assertTrue(AimbotRules.bruteSweepInFov(-59.9, 60.0));
        assertTrue(AimbotRules.bruteSweepInFov(60.0, 60.0));
        assertFalse(AimbotRules.bruteSweepInFov(60.1, 60.0));
        assertFalse(AimbotRules.bruteSweepInFov(120.0, 60.0));
        assertFalse(AimbotRules.bruteSweepInFov(Double.NaN, 60.0));
    }

    @Test
    void bruteSweepAdvancesLeftToRightThenRestarts() {
        double[] yaw = {-30.0, -5.0, 12.0, 40.0};
        assertEquals(0, AimbotRules.bruteSweepAdvance(yaw, Double.NaN, 0.25));
        assertEquals(0, AimbotRules.bruteSweepAdvance(yaw, -45.0, 0.25));
        assertEquals(1, AimbotRules.bruteSweepAdvance(yaw, -30.0, 0.25));
        assertEquals(2, AimbotRules.bruteSweepAdvance(yaw, -5.0, 0.25));
        assertEquals(3, AimbotRules.bruteSweepAdvance(yaw, 12.0, 0.25));
        assertEquals(0, AimbotRules.bruteSweepAdvance(yaw, 40.0, 0.25));
        assertEquals(-1, AimbotRules.bruteSweepAdvance(new double[0], 0.0, 0.25));
        assertEquals(-1, AimbotRules.bruteSweepAdvance(null, 0.0, 0.25));
    }

    @Test
    void threatMemoryAndCandidatePriorityMatchForgeRules() {
        long now = 10_000L;
        assertTrue(AimbotRules.isThreat(6.0, 6.0, 0L, now));
        assertTrue(AimbotRules.isThreat(9.0, 6.0, now + 4_000L, now));
        assertFalse(AimbotRules.isThreat(6.01, 6.0, 0L, now));
        assertFalse(AimbotRules.isThreat(9.0, 6.0, now - 1L, now));

        AimbotRules.Candidate threat = candidate(true, false, false, false, 8, 1.0);
        AimbotRules.Candidate too = candidate(false, true, false, true, 8, 0.1);
        AimbotRules.Candidate giant = candidate(false, false, true, true, 8, 0.1);
        AimbotRules.Candidate head = candidate(false, false, false, true, 3, 0.8);
        AimbotRules.Candidate body = candidate(false, false, false, false, 3, 0.1);
        AimbotRules.Candidate fewerPen = candidate(false, false, false, true, 1, 1.0);

        assertTrue(AimbotRules.compareCandidates(threat, too) < 0);
        assertTrue(AimbotRules.compareCandidates(too, giant) < 0);
        assertTrue(AimbotRules.compareCandidates(giant, head) < 0);
        assertTrue(AimbotRules.compareCandidates(head, body) < 0);
        assertTrue(AimbotRules.compareCandidates(fewerPen, head) < 0);
    }

    @Test
    void stickySwitchOnlyAcceptsStrictUpgrade() {
        AimbotRules.Candidate normal = candidate(false, false, false, false, 2, 0.5);
        AimbotRules.Candidate betterAngle = candidate(false, false, false, false, 2, 0.05);
        AimbotRules.Candidate fewerPen = candidate(false, false, false, false, 0, 0.9);
        AimbotRules.Candidate too = candidate(false, true, false, false, 0, 0.9);
        AimbotRules.Candidate threat = candidate(true, false, false, false, 0, 0.9);

        assertFalse(AimbotRules.shouldSwitch(normal, betterAngle, false));
        assertTrue(AimbotRules.shouldSwitch(normal, fewerPen, false));
        assertTrue(AimbotRules.shouldSwitch(normal, too, false));
        assertTrue(AimbotRules.shouldSwitch(normal, threat, false));

        assertTrue(AimbotRules.shouldSwitch(normal, threat, true));
        assertFalse(AimbotRules.shouldSwitch(normal, too, true));
        assertFalse(AimbotRules.shouldSwitch(threat, candidate(true, true, false, false, 0, 0.1), true));
    }

    @Test
    void groupPriorityAndClosestMarginMatchForgeRules() {
        assertEquals(0, AimbotRules.groupRank(false, false, false, true, false, false));
        assertEquals(2, AimbotRules.groupRank(true, false, false, true, false, false));
        assertEquals(1, AimbotRules.groupRank(false, true, false, false, true, false));
        assertEquals(1, AimbotRules.groupRank(false, false, true, false, false, true));
        assertEquals(0, AimbotRules.groupRank(false, true, false, true, false, false));

        assertFalse(AimbotRules.closestBetter(10.0, 13.0, 3.0));
        assertTrue(AimbotRules.closestBetter(10.0, 13.1, 3.0));
        assertFalse(AimbotRules.closestBetter(12.0, 10.0, 3.0));
    }

    @Test
    void joystickUsesConeTieAndDecay() {
        assertFalse(AimbotRules.isJoystickSwitching(14.9, 0.0, 15.0));
        assertTrue(AimbotRules.isJoystickSwitching(9.0, 12.0, 15.0));
        assertTrue(AimbotRules.joystickHoldCurrent(true, false, true));
        assertFalse(AimbotRules.joystickHoldCurrent(true, true, true));

        assertEquals(1, AimbotRules.joystickPickIdx(new double[]{30.0, 5.0, 40.0}, 45.0));
        assertEquals(1, AimbotRules.joystickPickIdx(new double[]{46.0, 45.0}, 45.0));
        assertEquals(-1, AimbotRules.joystickPickIdx(new double[]{46.0, 50.0}, 45.0));
        assertEquals(1, AimbotRules.joystickPickIdx(new double[]{30.0, 5.0, 5.0}, 45.0));

        float[] pushed = AimbotRules.applyJoystickOffset(170.0f, 80.0f, 40.0, -20.0);
        assertEquals(-150.0f, pushed[0], 1.0e-6f);
        assertEquals(90.0f, pushed[1], 1.0e-6f);
        assertEquals(-9.0, AimbotRules.joyDecay(-10.0, 0.9), 1.0e-9);
    }

    @Test
    void flickEntersImmediatelyAndExitsAfterSustainedSlowInput() {
        int[] accumulator = {0};
        assertFalse(AimbotRules.flickStep(false, 500.0, 700.0, 200, 16, accumulator));
        assertTrue(AimbotRules.flickStep(false, 800.0, 700.0, 200, 16, accumulator));
        assertEquals(0, accumulator[0]);

        boolean active = true;
        for (int i = 0; i < 12; i++) {
            active = AimbotRules.flickStep(active, 200.0, 700.0, 200, 16, accumulator);
            assertTrue(active);
        }
        assertFalse(AimbotRules.flickStep(active, 200.0, 700.0, 200, 16, accumulator));

        accumulator[0] = 100;
        active = AimbotRules.flickStep(true, 900.0, 700.0, 200, 16, accumulator);
        assertTrue(active);
        assertEquals(0, accumulator[0]);
        assertFalse(AimbotRules.flickStep(active, 100.0, 700.0, 200, 201, accumulator));
    }

    @Test
    void humanizeStepConvergesWithBoundedVelocity() {
        for (double target : new double[]{30.0, 90.0, 150.0, -150.0}) {
            double current = 0.0;
            double velocity = 0.0;
            double maxVelocity = 0.0;
            for (int i = 0; i < 2000; i++) {
                double distance = AimbotRules.angleDelta(current, target);
                velocity = AimbotRules.humanizeStep(distance, velocity,
                        30.0, 45.0, 9.0, 5.0);
                current += velocity;
                maxVelocity = Math.max(maxVelocity, Math.abs(velocity));
                if (Math.abs(AimbotRules.angleDelta(current, target)) < 0.05
                        && Math.abs(velocity) < 0.05) break;
            }
            assertTrue(Math.abs(AimbotRules.angleDelta(current, target)) < 0.2,
                    "target=" + target + " current=" + current);
            assertTrue(maxVelocity <= 30.0 + 1.0e-9);
        }
    }

    @Test
    void humanizeHelpersMatchLegacyGeometry() {
        assertEquals(0.0, AimbotRules.saccadePeakDegPerTick(0.0), 1.0e-9);
        assertEquals(30.0, AimbotRules.saccadePeakDegPerTick(30.0), 1.0e-9);
        assertEquals(60.0, AimbotRules.saccadePeakDegPerTick(180.0), 1.0e-9);
        assertEquals(21.6, AimbotRules.overshootForSwing(180.0, 31.0), 0.2);
        assertTrue(AimbotRules.headRadiusDeg(30.0) < 1.0);
        assertTrue(AimbotRules.headRadiusDeg(0.5) > 25.0);
        assertTrue(AimbotRules.faceUpTrigger(0.5, 0.5));
        assertFalse(AimbotRules.faceUpTrigger(0.5001, 0.5));
        assertEquals(-90.0, AimbotRules.faceUpPitchTarget(2.0, 1.62, 0.3), 1.0e-9);
        assertTrue(AimbotRules.faceUpPitchTarget(1.0, 3.62, 0.4) > 0.0);
    }

    @Test
    void faceUpExitsOnlyAfterTheConfiguredReactionDelay() {
        int[] exitCount = {0};
        assertTrue(AimbotRules.faceUpStep(false, true, true, 8, exitCount));
        for (int i = 0; i < 7; i++) {
            assertTrue(AimbotRules.faceUpStep(true, true, false, 8, exitCount));
        }
        assertEquals(7, exitCount[0]);
        assertFalse(AimbotRules.faceUpStep(true, true, false, 8, exitCount));
        assertEquals(0, exitCount[0]);
        assertFalse(AimbotRules.faceUpStep(false, false, true, 8, exitCount));
    }

    @Test
    void humanizeSweepAndPursuitRulesAreBounded() {
        assertEquals(2, AimbotRules.frontWindowPickIdx(
                new double[]{45.0, -20.0, 5.0, 31.0}, 30.0));
        assertTrue(AimbotRules.shouldSweepScan(2, 3.0, 3.0));
        assertFalse(AimbotRules.shouldSweepScan(1, 10.0, 3.0));
        assertEquals(10.0, AimbotRules.sweepScanAngleDeg(-10.0, 10.0, 0.25), 1.0e-9);
        assertTrue(AimbotRules.pursuitNeeded(-80.0, 0.0, 30.0));
        assertFalse(AimbotRules.pursuitNeededWithHysteresis(false, 29.9, 0.0, 30.0, 18.0));
        assertTrue(AimbotRules.pursuitNeededWithHysteresis(false, 30.0, 0.0, 30.0, 18.0));
        assertTrue(AimbotRules.pursuitNeededWithHysteresis(true, 20.0, 0.0, 30.0, 18.0));
        assertFalse(AimbotRules.pursuitNeededWithHysteresis(true, 17.9, 0.0, 30.0, 18.0));
        assertEquals(15.0, AimbotRules.smoothRotationStep(90.0, 30.0), 1.0e-9);
        assertEquals(5.0 / 6.0, AimbotRules.smoothRotationStep(5.0, 30.0), 1.0e-9);
        assertEquals(-30.0, AimbotRules.smoothRotationStep(-180.0, 30.0), 1.0e-9);
        assertEquals(10.0, AimbotRules.renderStepFromTickDelta(30.0, 1.0 / 60.0,
                1.0 / 20.0), 1.0e-9);
        assertEquals(30.0, AimbotRules.renderStepFromTickDelta(30.0, 1.0 / 20.0,
                1.0 / 20.0), 1.0e-9);
        assertEquals(0.0, AimbotRules.renderStepFromTickDelta(30.0, -1.0,
                1.0 / 20.0), 1.0e-9);
        assertEquals(4.0, AimbotRules.pursuitStep(80.0, 0.0, new Random(1)), 1.0e-9);
        assertEquals(0.05, AimbotRules.quantizeRotation(0.047), 1.0e-9);
        assertEquals(90.0, AimbotRules.bruteRotationStep(90.0, 180.0), 1.0e-9);
        assertEquals(-180.0, AimbotRules.bruteRotationStep(-240.0, 180.0), 1.0e-9);
        assertEquals(0.0, AimbotRules.bruteRotationStep(90.0, 0.0), 1.0e-9);
    }

    @Test
    void nearHumanizeNoiseShrinksDuringBallisticCorrection() {
        assertEquals(0.0, AimbotRules.tremorSuppressionScale(5.0, 5.0), 1.0e-9);
        assertEquals(0.0, AimbotRules.tremorSuppressionScale(20.0, 5.0), 1.0e-9);
        assertEquals(0.5, AimbotRules.tremorSuppressionScale(2.5, 5.0), 1.0e-9);
        assertEquals(1.0, AimbotRules.tremorSuppressionScale(0.0, 5.0), 1.0e-9);
        assertEquals(1.0, AimbotRules.tremorSuppressionScale(50.0, 0.0), 1.0e-9);
    }

    @Test
    void nearSweepAmplitudeIsCappedForCloseTargets() {
        assertEquals(0.8, AimbotRules.nearSweepErrAmplitude(1.0), 1.0e-9);
        assertEquals(3.0, AimbotRules.nearSweepErrAmplitude(8.5), 1.0e-9);
        assertEquals(3.0, AimbotRules.nearSweepErrAmplitude(20.0), 1.0e-9);
        assertEquals(2.4, AimbotRules.nearSweepErrAmplitude(3.0), 1.0e-9);
    }

    @Test
    void smallestTurnAngleAlwaysBeatsCloserOrThreateningCandidate() {
        // 5° 的目标必须赢 25° 的目标，哪怕 25° 那只更近、或 5° 那只不是威胁。
        assertEquals(1, AimbotRules.frontWindowPickIdx(new double[]{25.0, 5.0}, 30.0));
        assertEquals(1, AimbotRules.frontWindowPickIdx(new double[]{-25.0, 12.0}, 30.0));
        assertEquals(-1, AimbotRules.frontWindowPickIdx(new double[]{40.0, -35.0}, 30.0));
        assertTrue(AimbotRules.compareSweepCandidates(5.0, 25.0) < 0);
        assertTrue(AimbotRules.compareSweepCandidates(-5.0, -25.0) < 0);
        assertTrue(AimbotRules.compareSweepCandidates(25.0, 5.0) > 0);
        assertEquals(0, AimbotRules.compareSweepCandidates(-8.0, 8.0));
    }

    @Test
    void midCellCoversOnlyTheUfoDropArea() {
        // UFO 四口 (±2, 105, 12/14) 正下方
        assertTrue(AimbotRules.isInsideMidCell(0.0, 13.0));
        assertTrue(AimbotRules.isInsideMidCell(2.0, 12.0));
        assertTrue(AimbotRules.isInsideMidCell(-2.0, 14.0));
        assertTrue(AimbotRules.isInsideMidCell(3.0, 15.0));
        assertTrue(AimbotRules.isInsideMidCell(-3.0, 11.0));
        // 边界外：窗户/传送点/花坛边缘之外都不算
        assertFalse(AimbotRules.isInsideMidCell(3.5, 13.0));
        assertFalse(AimbotRules.isInsideMidCell(0.0, 15.5));
        assertFalse(AimbotRules.isInsideMidCell(0.0, 10.5));
        assertFalse(AimbotRules.isInsideMidCell(-6.0, 32.0));  // p1 传送点
        assertFalse(AimbotRules.isInsideMidCell(18.0, 44.0));  // alt 摩天轮角
    }

    @Test
    void highAbovePlayerUsesFootHeightDifferenceOnly() {
        // 玩家脚底 y=72：高台上的怪（77.5）超过 5 格阈值 → 忽略
        assertTrue(AimbotRules.isTooHighAbove(77.5, 72.0, 5.0));
        // 斜坡/矮台阶（76.0，差 4 格）→ 照常打
        assertFalse(AimbotRules.isTooHighAbove(76.0, 72.0, 5.0));
        // 阈值边界：正好 5.0 不算超过
        assertFalse(AimbotRules.isTooHighAbove(77.0, 72.0, 5.0));
        // 比玩家低的怪（地下室）永远不触发
        assertFalse(AimbotRules.isTooHighAbove(68.0, 72.0, 5.0));
        // 玩家自己在高处（脚底 100），怪在 104 → 只差 4 格，不忽略
        assertFalse(AimbotRules.isTooHighAbove(104.0, 100.0, 5.0));
        // 非法值
        assertFalse(AimbotRules.isTooHighAbove(Double.NaN, 72.0, 5.0));
        assertFalse(AimbotRules.isTooHighAbove(80.0, Double.NaN, 5.0));
    }

    @Test
    void critPitchToleranceStaysInsideTheHeadBand() {
        double footY = 72.0;
        double height = 1.95;                       // 普通僵尸
        double bandBottom = footY + AimbotRules.headLayerBottom(height);   // 73.56
        double bandTop = footY + height;                                   // 73.95
        double center = (bandBottom + bandTop) / 2.0;                      // 73.755

        // 爆头带中心：容差为正，且明显小于配置默认 2°（会收紧冻结判据）
        double tol10 = AimbotRules.critPitchToleranceDeg(center, footY, height, 10.0);
        assertTrue(tol10 > 0.0 && tol10 < 2.0, "10 格容差应在 (0,2) 内，实际 " + tol10);

        // 距离越远角度越小
        double tol5 = AimbotRules.critPitchToleranceDeg(center, footY, height, 5.0);
        double tol20 = AimbotRules.critPitchToleranceDeg(center, footY, height, 20.0);
        assertTrue(tol5 > tol10 && tol10 > tol20, "容差应随距离单调变紧");

        // 容差换算回线性余量后不得超出爆头层
        double linear = Math.tan(Math.toRadians(tol10)) * 10.0;
        assertTrue(linear <= (bandTop - bandBottom) / 2.0 + 1.0E-9,
                "容差不得越出爆头层，实际 " + linear);

        // 瞄准点已降级到身体（低于 80% 线）或高于头顶 -> 无暴击带可守
        assertEquals(0.0, AimbotRules.critPitchToleranceDeg(bandBottom - 0.1, footY, height, 10.0));
        assertEquals(0.0, AimbotRules.critPitchToleranceDeg(bandTop + 0.1, footY, height, 10.0));

        // 非法输入
        assertEquals(0.0, AimbotRules.critPitchToleranceDeg(Double.NaN, footY, height, 10.0));
        assertEquals(0.0, AimbotRules.critPitchToleranceDeg(center, footY, 0.0, 10.0));

        // 极远距离仍有下限，避免永不收敛
        assertTrue(AimbotRules.critPitchToleranceDeg(center, footY, height, 1000.0)
                >= AimbotRules.CRIT_TOL_FLOOR_DEG);
    }

    @Test
    void highSpeedFallIgnoresOnlyAirborneMidDrops() {
        double groundY = 76.0;
        double fallSpeed = 1.8;
        double maxHorizontal = 0.5;
        // UFO 投放：y=95、垂直 -3.2、几乎无水平位移 → 命中
        assertTrue(AimbotRules.isHighSpeedFall(95.0, -3.2, 0.05,
                groundY, fallSpeed, maxHorizontal));
        // 刚离飞船、速度还不够快 → 不命中（等它加速到阈值再忽略）
        assertFalse(AimbotRules.isHighSpeedFall(104.0, -0.4, 0.0,
                groundY, fallSpeed, maxHorizontal));
        // 已落地（y<=76）→ 必须恢复为普通目标
        assertFalse(AimbotRules.isHighSpeedFall(72.0, -3.2, 0.0,
                groundY, fallSpeed, maxHorizontal));
        // 被打飞：垂直很快但水平位移明显 → 不能被忽略
        assertFalse(AimbotRules.isHighSpeedFall(80.0, -3.0, 1.4,
                groundY, fallSpeed, maxHorizontal));
        // 窗户下落：贴地、速度小 → 不能被忽略
        assertFalse(AimbotRules.isHighSpeedFall(73.5, -0.6, 0.1,
                groundY, fallSpeed, maxHorizontal));
        // 非法值
        assertFalse(AimbotRules.isHighSpeedFall(Double.NaN, -3.0, 0.0,
                groundY, fallSpeed, maxHorizontal));
        assertFalse(AimbotRules.isHighSpeedFall(90.0, Double.NaN, 0.0,
                groundY, fallSpeed, maxHorizontal));
    }
}

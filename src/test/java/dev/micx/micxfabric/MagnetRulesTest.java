package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagnetRulesTest {
    @Test
    void yawDiffSignMatchesTurnDirection() {
        // 看向 +Z（yaw=0），目标在 -X（西，MC 里是右手侧）→ targetYaw=+90（右转）
        double diff = MagnetRules.yawToTarget(-1, 0, 0);
        assertEquals(90.0, diff, 0.001);
        // 目标在 +X（东，左手侧）→ targetYaw=-90（左转）
        assertEquals(-90.0, MagnetRules.yawToTarget(1, 0, 0), 0.001);
    }

    @Test
    void pitchDiffPositiveMeansLookDown() {
        // 目标在正下方（dirY=-1），pitch=0 → 需低头（+90）
        assertEquals(90.0, MagnetRules.pitchToTarget(-1, 0), 0.001);
        // 目标正前方，当前 pitch=0 → 无需动
        assertEquals(0.0, MagnetRules.pitchToTarget(0, 0), 0.001);
    }

    @Test
    void axisCorrectionNeverOvershootsAndKeepsSign() {
        assertEquals(2.0, MagnetRules.axisCorrection(8.0, 2.0), 0.001);
        assertEquals(-2.0, MagnetRules.axisCorrection(-8.0, 2.0), 0.001);
        assertEquals(8.0, MagnetRules.axisCorrection(8.0, 10.0), 0.001);
        assertEquals(0.0, MagnetRules.axisCorrection(5.0, 0.0), 0.001);
    }

    @Test
    void humanStepConvergesFastThenSlow() {
        double first = MagnetRules.humanStep(8.0, 1.8, 0.1);
        double second = MagnetRules.humanStep(8.0 * (1 - first / 8.0), 1.8, 0.1);
        assertTrue(first > second);   // 剩余差距变小 → 单步修正变小（先快后慢）
        assertTrue(MagnetRules.humanStep(8.0, 1.8, 0) == 0.0);
    }

    @Test
    void speedFactorDegradesWithMouseSpeed() {
        assertEquals(1.0, MagnetRules.speedFactor(0, 15, 120), 0.001);
        assertEquals(0.0, MagnetRules.speedFactor(200, 15, 120), 0.001);
        double mid = MagnetRules.speedFactor(67.5, 15, 120);
        assertTrue(mid > 0 && mid < 1);
    }

    @Test
    void headshotStopRangeLooseNearTightFar() {
        assertTrue(MagnetRules.headshotStopRange(0.05, 1.5) > 30.0);   // 贴脸松
        assertTrue(MagnetRules.headshotStopRange(30.0, 1.5) >= 1.5);   // 远处兜底
    }

    @Test
    void acceptsEntityTypeHonorsSwitches() {
        assertFalse(MagnetRules.acceptsEntityType(true, false, false, false, true, true));
        assertTrue(MagnetRules.acceptsEntityType(false, true, false, false, true, true));
        assertTrue(MagnetRules.acceptsEntityType(false, false, false, false, false, false));
    }

    @Test
    void rayChordDeepestThroughCenter() {
        // 从 z=-5 沿 +Z 射向 1×1×1 盒子：穿心弦深 1，擦边（x=0.49）弦深仍 1 但命中点更远
        double center = MagnetRules.rayChordDepth(0, 0, -5, 0, 0, 1,
                -0.5, -0.5, 0, 0.5, 0.5, 1);
        assertEquals(1.0, center, 0.001);
        double miss = MagnetRules.rayIntersectsAabb(2, 0, -5, 0, 0, 1,
                -0.5, -0.5, 0, 0.5, 0.5, 1);
        assertEquals(-1.0, miss, 0.001);
    }
}

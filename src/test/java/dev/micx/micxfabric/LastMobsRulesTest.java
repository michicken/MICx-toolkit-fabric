package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastMobsRulesTest {
    @Test
    void isActiveRequiresCountInRange() {
        assertTrue(LastMobsRules.isActive(1, 5));
        assertTrue(LastMobsRules.isActive(5, 5));
        assertFalse(LastMobsRules.isActive(6, 5));
        assertFalse(LastMobsRules.isActive(0, 5));
        assertFalse(LastMobsRules.isActive(-1, 5));
    }

    @Test
    void projectsPointStraightAheadToScreenCenter() {
        // 相机在原点看向 +Z（yaw=0, pitch=0），目标正前方 10 格
        float[] point = LastMobsRules.project(0, 0, 10, 0f, 0f, 70f, 16.0 / 9.0, 1920, 1080);
        assertNotNull(point);
        assertEquals(960f, point[0], 1.0f);
        assertEquals(540f, point[1], 1.0f);
    }

    @Test
    void targetBehindCameraReturnsNull() {
        assertNull(LastMobsRules.project(0, 0, -10, 0f, 0f, 70f, 16.0 / 9.0, 1920, 1080));
        // 正对天/地时右向量退化
        assertNull(LastMobsRules.project(0, 10, 0, 0f, 90f, 70f, 16.0 / 9.0, 1920, 1080));
    }

    @Test
    void rightOfViewMapsRightOnScreen() {
        // 看向 +Z（yaw=0），MC 里右侧是 -X；目标在 -X 偏移 → 屏幕右半边（x > 中心）
        float[] point = LastMobsRules.project(-4, 0, 10, 0f, 0f, 70f, 16.0 / 9.0, 1920, 1080);
        assertNotNull(point);
        assertTrue(point[0] > 960f);
    }

    @Test
    void clampsOffScreenPointsIntoMargin() {
        float[] clamped = LastMobsRules.clampToScreen(-50, 5000, 1920, 1080, 4);
        assertEquals(4f, clamped[0], 0.001f);
        assertEquals(1076f, clamped[1], 0.001f);
    }
}

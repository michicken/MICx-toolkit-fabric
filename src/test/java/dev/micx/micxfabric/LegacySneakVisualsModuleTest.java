package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 1.8 潜行眼高判定的回归测试。
 *
 * <p>守住三条边界：潜行+启用 → 1.54；站立 → 不动；模块关闭 → 一律不动（潜行也不动）。
 * 这三条一旦被改错，本地命中射线、相机、服务端就会互相错位，直接表现为瞄空。
 */
class LegacySneakVisualsModuleTest {

    /** 26.2 现代潜行 pose 眼高。 */
    private static final float MODERN_CROUCH = 1.27f;
    /** 26.2 站立 pose 眼高。 */
    private static final float STANDING = 1.62f;

    @Test
    void crouchingWithModuleEnabledUsesLegacyHeight() {
        assertEquals(1.54f, LegacySneakVisualsModule.effectiveEyeHeight(true, true, MODERN_CROUCH),
                1.0e-6f);
    }

    @Test
    void standingKeepsTheGivenHeight() {
        assertEquals(STANDING, LegacySneakVisualsModule.effectiveEyeHeight(true, false, STANDING),
                1.0e-6f);
    }

    @Test
    void disabledModuleNeverTouchesTheHeight() {
        assertEquals(MODERN_CROUCH,
                LegacySneakVisualsModule.effectiveEyeHeight(false, true, MODERN_CROUCH), 1.0e-6f);
        assertEquals(STANDING,
                LegacySneakVisualsModule.effectiveEyeHeight(false, false, STANDING), 1.0e-6f);
    }

    /** 1.54 必须正好等于 现代潜行眼高 + DELTA_Y，防止两个常量各改一半。 */
    @Test
    void legacyHeightIsExactlyModernCrouchPlusDelta() {
        assertEquals(LegacySneakVisualsModule.VISUAL_SNEAK_EYE_HEIGHT,
                LegacySneakVisualsModule.MODERN_SNEAK_EYE_HEIGHT + LegacySneakVisualsModule.DELTA_Y,
                1.0e-6f);
        assertEquals(LegacySneakVisualsModule.VISUAL_SNEAK_EYE_HEIGHT,
                LegacySneakVisualsModule.effectiveEyeHeight(true, true,
                        LegacySneakVisualsModule.MODERN_SNEAK_EYE_HEIGHT),
                1.0e-6f);
    }
}

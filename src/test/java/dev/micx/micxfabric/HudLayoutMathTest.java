package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudLayoutMathTest {
    @Test
    void resizeUsesIndependentHorizontalAndVerticalScales() {
        assertEquals(1.5f, HudLayoutMath.resizeScale(150, 0, 100, 1.0f), 0.001f);
        assertEquals(0.5f, HudLayoutMath.resizeScale(20, 0, 100, 1.0f), 0.001f);
        assertEquals(2.0f, HudLayoutMath.resizeScale(500, 0, 100, 1.0f), 0.001f);
    }

    @Test
    void renderSizeIncludesModuleAndLayoutScales() {
        assertEquals(150, HudLayoutMath.renderSize(100, 1.0f, 1.5f));
        assertEquals(120, HudLayoutMath.renderSize(100, 1.2f, 1.0f));
        assertEquals(50, HudLayoutMath.renderSize(100, 1.0f, 0.1f));
    }

    @Test
    void resizeHonorsAvailableScreenSpace() {
        assertEquals(1.5f, HudLayoutMath.resizeScale(150, 0, 100, 1.0f, 200), 0.001f);
        assertEquals(1.0f, HudLayoutMath.resizeScale(250, 0, 100, 1.0f, 100), 0.001f);
        assertEquals(0.5f, HudLayoutMath.resizeScale(10, 0, 100, 1.0f, 30), 0.001f);
    }

    @Test
    void positionsStayInsideScreenBounds() {
        assertEquals(0, HudLayoutMath.clampPosition(-20, 100, 640));
        assertEquals(540, HudLayoutMath.clampPosition(999, 100, 640));
        assertEquals(0, HudLayoutMath.clampPosition(20, 100, 40));
    }

    @Test
    void resizeHandleAndBlockHitTestsUseRenderedRectangle() {
        assertTrue(HudLayoutMath.contains(109, 59, 10, 20, 100, 40));
        assertFalse(HudLayoutMath.contains(110, 60, 10, 20, 100, 40));
        assertTrue(HudLayoutMath.inResizeHandle(104, 54, 10, 20, 100, 40));
        assertFalse(HudLayoutMath.inResizeHandle(101, 51, 10, 20, 100, 40));
    }

    @Test
    void invalidScalesFallBackToStableRange() {
        assertEquals(1.0f, HudLayoutMath.clampScale(Float.NaN), 0.001f);
        assertEquals(1.0f, HudLayoutMath.clampScale(Float.POSITIVE_INFINITY), 0.001f);
        assertEquals(0.5f, HudLayoutMath.clampScale(0.1f), 0.001f);
        assertEquals(2.0f, HudLayoutMath.clampScale(3.0f), 0.001f);
    }
}

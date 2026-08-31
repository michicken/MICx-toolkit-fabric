package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoomScopeStateTest {

    @Test
    void defaultZoomIsFour() {
        ZoomScopeState s = new ZoomScopeState(ZoomScopeState.DEFAULT_ZOOM);
        assertEquals(4, s.zoom());
    }

    @Test
    void zoomClampedToRange() {
        assertEquals(ZoomScopeState.MIN_ZOOM, ZoomScopeState.clampZoom(1));
        assertEquals(5, ZoomScopeState.clampZoom(5));
        assertEquals(ZoomScopeState.MAX_ZOOM, ZoomScopeState.clampZoom(9));
    }

    @Test
    void wheelUpZoomsIn() {
        ZoomScopeState s = new ZoomScopeState(4);
        assertEquals(5, s.adjustZoom(120));
        assertEquals(6, s.adjustZoom(1));
    }

    @Test
    void wheelDownZoomsOut() {
        ZoomScopeState s = new ZoomScopeState(4);
        assertEquals(3, s.adjustZoom(-120));
        assertEquals(2, s.adjustZoom(-1));
    }

    @Test
    void activeOnlyWhenHeldAndUnblocked() {
        ZoomScopeState s = new ZoomScopeState(4);
        assertFalse(s.isActive());
        assertTrue(s.observe(true, false));
        assertTrue(s.isActive());
        assertFalse(s.observe(false, false));
        assertFalse(s.isActive());
    }

    @Test
    void blockedWhileHeldDeactivates() {
        ZoomScopeState s = new ZoomScopeState(4);
        s.observe(true, false);
        assertFalse(s.observe(true, true));
        assertTrue(s.observe(true, false));
    }

    @Test
    void sensitivityFactorCurveIsOneOverZoomAtK1() {
        float[] expected = {1f/2f, 1f/3f, 1f/4f, 1f/5f, 1f/6f, 1f/7f, 1f/8f};
        for (int z = ZoomScopeState.MIN_ZOOM; z <= ZoomScopeState.MAX_ZOOM; z++) {
            ZoomScopeState s = new ZoomScopeState(z);
            s.observe(true, false);
            assertEquals(expected[z - ZoomScopeState.MIN_ZOOM], s.sensitivityFactor(), 1e-6f);
        }
    }

    @Test
    void sensitivityFactorScalesWithK() {
        ZoomScopeState s = new ZoomScopeState(2);
        s.setSensitivityK(2f);
        s.observe(true, false);
        assertEquals(1.0f, s.sensitivityFactor(), 1e-6f);
        s.adjustZoom(120);
        assertEquals(2f/3f, s.sensitivityFactor(), 1e-6f);
        s.adjustZoom(120);
        assertEquals(0.5f, s.sensitivityFactor(), 1e-6f);
    }

    @Test
    void scaleDeltaAccumulatesSubPixel() {
        ZoomScopeState s = new ZoomScopeState(8);
        s.observe(true, false);
        for (int i = 0; i < 7; i++) assertEquals(0, s.scaleDeltaX(1));
        assertEquals(1, s.scaleDeltaX(1));
    }

    @Test
    void zoomedFovScalesExactly() {
        double fov = Math.toRadians(70.0);
        double zf = ZoomScopeState.zoomedFovRad(fov, 4);
        assertEquals(Math.tan(fov/2.0)/4.0, Math.tan(zf/2.0), 1e-9);
        assertTrue(zf < fov);
    }

    @Test
    void blitSourceVCentersOnWiderThan169() {
        float[] v = ZoomScopeState.blitSourceV(720f, 450f);
        assertEquals(0.05f, v[0], 1e-6f);
        assertEquals(0.95f, v[1], 1e-6f);
    }

    @Test
    void blitSourceVFullFrameOn169() {
        float[] v = ZoomScopeState.blitSourceV(1920f, 1080f);
        assertEquals(0f, v[0], 1e-9f);
        assertEquals(1f, v[1], 1e-9f);
    }
}

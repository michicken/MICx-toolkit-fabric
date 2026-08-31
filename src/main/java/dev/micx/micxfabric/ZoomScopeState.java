package dev.micx.micxfabric;

/**
 * ZoomScope 状态机 — 1:1 移植自 {@code zoom/ZoomScopeState.java}。
 * 按住放大：中央 50% 宽 16:9 矩形清晰放大，松开瞬时消失，无平滑。
 */
public final class ZoomScopeState {

    public static final int MIN_ZOOM = 2;
    public static final int MAX_ZOOM = 8;
    public static final int DEFAULT_ZOOM = 4;

    static final float REGION_WIDTH_RATIO = 1f / 2f;
    static final float REGION_ASPECT = 16f / 9f;

    private boolean active;
    private int zoom;

    private double accX;
    private double accY;

    public float sensK = 1f;

    ZoomScopeState(int zoom) {
        this.zoom = clampZoom(zoom);
    }

    static int clampZoom(int z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    public static double zoomedFovRad(double fovRad, double zoom) {
        return 2.0 * Math.atan(Math.tan(fovRad / 2.0) / zoom);
    }

    public static float[] blitSourceV(float fboW, float fboH) {
        float cropH = fboW * 9f / 16f;
        if (cropH >= fboH) {
            return new float[] {0f, 1f};
        }
        float v0 = (fboH - cropH) / 2f / fboH;
        float v1 = (fboH + cropH) / 2f / fboH;
        return new float[] {v0, v1};
    }

    public int zoom() {
        return zoom;
    }

    public boolean isActive() {
        return active;
    }

    public float sensitivityFactor() {
        return active ? sensK / zoom : 1f;
    }

    void setSensitivityK(float k) {
        sensK = Math.max(0.5f, Math.min(3.0f, k));
    }

    int scaleDeltaX(int raw) {
        double v = accX + raw * (double) sensitivityFactor();
        int out = (int) v;
        accX = v - out;
        return out;
    }

    int scaleDeltaY(int raw) {
        double v = accY + raw * (double) sensitivityFactor();
        int out = (int) v;
        accY = v - out;
        return out;
    }

    /** 26.2 鼠标链路是 double accumulatedDX/DY，双精度版本。 */
    double scaleDouble(double raw) {
        return raw * sensitivityFactor();
    }

    boolean observe(boolean keyDown, boolean blocked) {
        active = !blocked && keyDown;
        return active;
    }

    int adjustZoom(int dwheel) {
        if (dwheel > 0) {
            zoom = clampZoom(zoom + 1);
        } else if (dwheel < 0) {
            zoom = clampZoom(zoom - 1);
        }
        return zoom;
    }

    void reset() {
        active = false;
        accX = 0;
        accY = 0;
    }
}

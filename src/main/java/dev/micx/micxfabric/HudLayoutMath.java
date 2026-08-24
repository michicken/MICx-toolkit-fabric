package dev.micx.micxfabric;

/** Pure geometry rules shared by the HUD layout editor and its tests. */
public final class HudLayoutMath {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;
    public static final int HANDLE_SIZE = 8;

    private HudLayoutMath() {
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clampScale(float value) {
        if (!Float.isFinite(value)) return 1.0f;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    public static int renderSize(int baseSize, float moduleScale, float layoutScale) {
        float safeBase = Math.max(1, baseSize);
        float safeModule = Float.isFinite(moduleScale) ? Math.max(0.1f, moduleScale) : 1.0f;
        return Math.max(1, Math.round(safeBase * safeModule * clampScale(layoutScale)));
    }

    public static int clampPosition(int value, int size, int screenSize) {
        return clamp(value, 0, Math.max(0, screenSize - Math.max(1, size)));
    }

    public static float resizeScale(int mouse, int origin, int baseSize, float moduleScale) {
        return resizeScale(mouse, origin, baseSize, moduleScale, Integer.MAX_VALUE);
    }

    public static float resizeScale(int mouse, int origin, int baseSize, float moduleScale,
                                    int availableSpace) {
        float safeModule = Float.isFinite(moduleScale) ? Math.max(0.1f, moduleScale) : 1.0f;
        float denominator = Math.max(1.0f, Math.max(1, baseSize) * safeModule);
        float requested = (mouse - origin) / denominator;
        float available = Math.max(0, availableSpace) / denominator;
        float dynamicMax = Math.max(MIN_SCALE, Math.min(MAX_SCALE, available));
        return Math.max(MIN_SCALE, Math.min(dynamicMax, requested));
    }

    public static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + Math.max(1, width)
                && mouseY >= y && mouseY < y + Math.max(1, height);
    }

    public static boolean inResizeHandle(int mouseX, int mouseY, int x, int y, int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int handleX = x + Math.max(0, safeWidth - HANDLE_SIZE);
        int handleY = y + Math.max(0, safeHeight - HANDLE_SIZE);
        return mouseX >= handleX && mouseX < x + safeWidth
                && mouseY >= handleY && mouseY < y + safeHeight;
    }

    public static int dragOffset(double mouse, int origin) {
        return (int) Math.round(mouse - origin);
    }
}

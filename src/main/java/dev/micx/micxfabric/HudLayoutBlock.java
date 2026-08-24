package dev.micx.micxfabric;

/** One editable screen-space HUD preview and its configuration adapter. */
public final class HudLayoutBlock {
    public interface Adapter {
        int x(int screenWidth, int screenHeight, int renderWidth, int renderHeight);

        int y(int screenWidth, int screenHeight, int renderWidth, int renderHeight);

        void setPosition(int x, int y, int screenWidth, int screenHeight, int renderWidth, int renderHeight);

        void resetPosition();

        void save();

        default float moduleScaleX() {
            return 1.0f;
        }

        default float moduleScaleY() {
            return 1.0f;
        }

        default void resetScale() {
        }
    }

    private final String id;
    private final String label;
    private final String sample;
    private final int baseWidth;
    private final int baseHeight;
    private final Adapter adapter;

    public HudLayoutBlock(String id, String label, String sample, int baseWidth, int baseHeight, Adapter adapter) {
        this.id = id;
        this.label = label;
        this.sample = sample;
        this.baseWidth = Math.max(1, baseWidth);
        this.baseHeight = Math.max(1, baseHeight);
        this.adapter = adapter;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String sample() {
        return sample;
    }

    public int baseWidth() {
        return baseWidth;
    }

    public int baseHeight() {
        return baseHeight;
    }

    public int renderWidth() {
        return HudLayoutMath.renderSize(baseWidth, adapter.moduleScaleX(),
                HudLayoutConfig.instance().scaleX(id));
    }

    public int renderHeight() {
        return HudLayoutMath.renderSize(baseHeight, adapter.moduleScaleY(),
                HudLayoutConfig.instance().scaleY(id));
    }

    public int x(int screenWidth, int screenHeight) {
        return HudLayoutMath.clampPosition(
                adapter.x(screenWidth, screenHeight, renderWidth(), renderHeight()),
                renderWidth(), screenWidth);
    }

    public int y(int screenWidth, int screenHeight) {
        return HudLayoutMath.clampPosition(
                adapter.y(screenWidth, screenHeight, renderWidth(), renderHeight()),
                renderHeight(), screenHeight);
    }

    public boolean contains(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        return HudLayoutMath.contains(mouseX, mouseY, x(screenWidth, screenHeight),
                y(screenWidth, screenHeight), renderWidth(), renderHeight());
    }

    public boolean inResizeHandle(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        return HudLayoutMath.inResizeHandle(mouseX, mouseY, x(screenWidth, screenHeight),
                y(screenWidth, screenHeight), renderWidth(), renderHeight());
    }

    public void setPosition(int x, int y, int screenWidth, int screenHeight) {
        int width = renderWidth();
        int height = renderHeight();
        adapter.setPosition(
                HudLayoutMath.clampPosition(x, width, screenWidth),
                HudLayoutMath.clampPosition(y, height, screenHeight),
                screenWidth, screenHeight, width, height);
    }

    public void setScaleFromMouse(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        setScaleFromMouse(mouseX, mouseY, screenWidth, screenHeight,
                x(screenWidth, screenHeight), y(screenWidth, screenHeight));
    }

    public void setScaleFromMouse(double mouseX, double mouseY, int screenWidth, int screenHeight,
                                  int originX, int originY) {
        float sx = HudLayoutMath.resizeScale((int) Math.round(mouseX), originX,
                baseWidth, adapter.moduleScaleX(), screenWidth - originX);
        float sy = HudLayoutMath.resizeScale((int) Math.round(mouseY), originY,
                baseHeight, adapter.moduleScaleY(), screenHeight - originY);
        HudLayoutConfig.instance().setScale(id, sx, sy);
        int width = renderWidth();
        int height = renderHeight();
        adapter.setPosition(
                HudLayoutMath.clampPosition(originX, width, screenWidth),
                HudLayoutMath.clampPosition(originY, height, screenHeight),
                screenWidth, screenHeight, width, height);
    }

    public void reset() {
        adapter.resetPosition();
        adapter.resetScale();
        HudLayoutConfig.instance().reset(id);
    }

    public void save() {
        adapter.save();
    }

    public float scaleX() {
        return HudLayoutConfig.instance().scaleX(id);
    }

    public float scaleY() {
        return HudLayoutConfig.instance().scaleY(id);
    }
}

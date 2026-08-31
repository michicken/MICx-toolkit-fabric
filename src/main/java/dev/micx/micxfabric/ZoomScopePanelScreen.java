package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

public final class ZoomScopePanelScreen extends ModuleConfigScreen {

    private final ZoomScopeModule mod;
    private int toggleY;
    private int minusY;
    private int plusY;

    public ZoomScopePanelScreen(Screen parent, ZoomScopeModule mod) {
        super(parent, "ZoomScope", "放大镜 · 按住放大 + 滚轮调倍率");
        this.mod = mod;
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
        section(g, "STATUS / 状态", y);
        y += 20;
        g.text(font, "Enable", contentLeft(), y + 4, TEXT);
        toggleY = y + 1;
        drawToggle(g, contentRight() - 44, y + 1, 44, 16, mod.enabled(), isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 34;

        g.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 12;
        section(g, "KEYBIND / 快捷键", y);
        y += 20;
        y = wrapped(g, "按住局部放大，松开恢复；按住时滚轮调 2~8x。单键绑定，支持鼠标侧键。", contentLeft(), y, TEXT_DIM, contentWidth()) + 4;
        y += 6;
        g.text(font, "当前倍率：" + mod.zoomFactor() + "x（范围 2~8x）", contentLeft(), y, TEXT);
        y += 14;
        y = wrapped(g, "按住放大键期间滚动滚轮：上滚放大 / 下滚缩小，调整后立即保存。", contentLeft(), y, TEXT_FAINT, contentWidth()) + 6;

        g.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 12;
        section(g, "SENSITIVITY / 灵敏度", y);
        y += 20;
        y = wrapped(g, "放大时灵敏度 = k/倍率。k=1 为 1:1 手感（4x→1/4），k=2 整体抬高（4x→1/2）。", contentLeft(), y, TEXT_DIM, contentWidth()) + 4;
        String label = String.format(java.util.Locale.ROOT, "Sensitivity K: %.1f  (4x→1/%.1f)", mod.sensitivityK(), Math.max(1.0, 4.0 / mod.sensitivityK()));
        g.text(font, label, contentLeft(), y + 4, TEXT);
        int minusX = contentRight() - 80;
        int plusX = contentRight() - 44;
        minusY = y + 1;
        plusY = y + 1;
        drawButton(g, "-", minusX, y + 1, 32, 16, isInside(mouseX, mouseY, minusX, y + 1, 32, 16));
        drawButton(g, "+", plusX, y + 1, 32, 16, isInside(mouseX, mouseY, plusX, y + 1, 32, 16));
        y += 26;

        g.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 12;
        section(g, "ABOUT / 说明", y);
        y += 20;
        y = wrapped(g, "按住快捷键：屏幕中央 16:9 矩形内显示清晰的局部放大画面（当前为全屏 FOV 缩放，画中画二期用小 FBO 实现），周边保持正常视野，松开立即消失，无平滑过渡。", contentLeft(), y, TEXT_DIM, contentWidth()) + 4;
        y = wrapped(g, "放大时鼠标灵敏度按 k/倍率降低，每个倍率固定对应、即时生效。纯客户端渲染，不发包。", contentLeft(), y, TEXT_DIM, contentWidth()) + 6;

        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int tx = contentRight() - 44;
            if (isInside(event.x(), event.y(), tx, toggleY, 44, 16)) {
                mod.setEnabled(!mod.enabled());
                return true;
            }
            if (isInside(event.x(), event.y(), contentRight() - 80, minusY, 32, 16)) {
                float k = mod.sensitivityK() - 0.1f;
                mod.setSensitivityK(Math.round(k * 10) / 10f);
                return true;
            }
            if (isInside(event.x(), event.y(), contentRight() - 44, plusY, 32, 16)) {
                float k = mod.sensitivityK() + 0.1f;
                mod.setSensitivityK(Math.round(k * 10) / 10f);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}

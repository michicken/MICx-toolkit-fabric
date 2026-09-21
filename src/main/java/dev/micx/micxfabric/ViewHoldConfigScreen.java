package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class ViewHoldConfigScreen extends ModuleConfigScreen {
    private static final int BUTTON_W = 110;

    public ViewHoldConfigScreen(Screen parent) {
        super(parent, UiText.shown("临时视角", "ViewHold"), UiText.shown("ViewHold · 按住切换，松开恢复", "按住切视角 · 目标视角与俯仰镜像"));
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        ViewHoldModule mod = ViewHoldModule.instance();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, mod.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 34;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 12;
        section(graphics, UiText.shown("目标视角", "TARGET VIEW / 目标视角"), y);
        y += 20;
        boolean behind = mod.getTargetView() == 1;
        String viewLabel = behind ? "背面视角" : "正面视角";
        String viewDesc = behind ? "按住时切到背后第三人称（1）" : "按住时切到正面第三人称（2）";
        graphics.text(font, "目标视角", contentLeft(), y + 4, TEXT);
        graphics.text(font, viewDesc, contentLeft(), y + 17, TEXT_FAINT);
        drawButton(graphics, viewLabel, contentRight() - BUTTON_W, y + 1, BUTTON_W, 16,
                isInside(mouseX, mouseY, contentRight() - BUTTON_W, y + 1, BUTTON_W, 16));
        y += 38;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 12;
        graphics.text(font, "俯仰镜像 Pitch Mirror", contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, mod.isPitchMirror(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 26;
        y = wrapped(graphics, UiText.shown("切到正面视角时会在这一帧里取反俯仰角，相机就像翻到你面前上方往下看你。", "正面视角时渲染帧内取反 pitch（相机翻到面前上方俯视自己）。"), contentLeft(), y, TEXT_DIM, contentWidth()) + 6;

        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            ViewHoldModule mod = ViewHoldModule.instance();
            int toggleX = contentRight() - 44;
            if (isInside(event.x(), event.y(), toggleX, contentY() + 1, 44, 16)) {
                ViewHoldModule.instance().setEnabled(!mod.enabled());
                return true;
            }
            // Target View button row
            int rowTop = contentTop() - scrollOffset();
            // y progression: STATUS 20 + 34 + 12 + TARGET sec 20 + button row start; compute via same offsets
            // Simpler: hit-test via known x
            int buttonY = contentY() + 20 + 34 + 12 + 20 + 1;
            if (isInside(event.x(), event.y(), contentRight() - BUTTON_W, buttonY, BUTTON_W, 16)) {
                mod.setTargetView(mod.getTargetView() == 1 ? 2 : 1);
                return true;
            }
            int pitchY = buttonY + 38 + 12;
            if (isInside(event.x(), event.y(), toggleX, pitchY + 1, 44, 16)) {
                mod.setPitchMirror(!mod.isPitchMirror());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}

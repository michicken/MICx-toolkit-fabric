package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** Functional DPS overlay settings. */
public final class DpsCounterConfigScreen extends ModuleConfigScreen {
    public DpsCounterConfigScreen(Screen parent) {
        super(parent, UiText.shown("DPS 计数", "DPSCounter"), UiText.shown("DPSCounter · 一秒滚动窗口", "DPS 计数 · 一秒滚动窗口"));
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        DpsCounterModule module = DpsCounterModule.instance();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("按血量（含吸收盾）掉多少来算伤害数字。", "跟踪实体生命值和吸收值的下降量。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("显示方式", "Overlay"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("在右上角显示最近一秒你打出的伤害总量。", "右上角显示最近一秒的伤害总量。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.overlayEnabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("使用限制", "BOUNDARY / 边界"), y);
        y += 20;
        y = wrapped(graphics, UiText.shown("换世界会清空记录重新开始，实体消失后不会留着旧数据。伤害完全在本地计算，不发包。", "世界切换会清空实体快照和滚动窗口；实体消失后不保留旧 ID。数值完全在客户端计算。"),
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        y = wrapped(graphics, UiText.shown("HUD 目前固定在右上角，和 Forge 版布局一致。", "当前 HUD 位置沿用 Forge 的右上角布局；HUD Layout 编辑器尚未迁移。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                DpsCounterModule.instance().setEnabled(!DpsCounterModule.instance().enabled());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                DpsCounterModule module = DpsCounterModule.instance();
                module.setOverlayEnabled(!module.overlayEnabled());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}

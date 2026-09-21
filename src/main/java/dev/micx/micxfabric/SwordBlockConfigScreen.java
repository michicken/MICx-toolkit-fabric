package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** SwordBlock details: client-side visual animation only. */
public final class SwordBlockConfigScreen extends ModuleConfigScreen {
    public SwordBlockConfigScreen(Screen parent) {
        super(parent, UiText.shown("剑格挡动画", "SwordBlock"), UiText.shown("SwordBlock · 客户端视觉", "剑格挡 · 客户端视觉设置"));
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 3, TEXT);
        graphics.text(font, "右键手持剑时使用 1.7 风格的 BLOCK 视觉动画。", contentLeft(), y + 16, TEXT_DIM);
        int toggleX = contentRight() - 44;
        int toggleY = y + 2;
        drawToggle(graphics, toggleX, toggleY, 44, 16, SwordBlockModule.instance().enabled(),
                isInside(mouseX, mouseY, toggleX, toggleY, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("使用限制", "BOUNDARY / 边界"), y);
        y += 20;
        y = wrapped(graphics,
                "这是客户端第一人称视觉格挡，不会添加盾牌，不提供服务端伤害减免，也不会发送自定义网络包。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 12;
        y = wrapped(graphics,
                "关闭模块只会停止 MICx 的剑动画 Mixin 行为；原版物品使用逻辑仍由游戏负责。",
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int y = contentTopForLayout() + 20;
            int toggleY = y + 2;
            if (isInside(event.x(), event.y(), contentRight() - 44, toggleY, 44, 16)) {
                SwordBlockModule.instance().setEnabled(!SwordBlockModule.instance().enabled());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int contentTopForLayout() {
        return contentY();
    }
}

package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** ChatCleaner configuration, limited to the behavior implemented by Fabric. */
public final class ChatCleanerConfigScreen extends ModuleConfigScreen {
    public ChatCleanerConfigScreen(Screen parent) {
        super(parent, UiText.shown("聊天清理", "ChatCleaner"), UiText.shown("ChatCleaner · 重复消息折叠", "聊天折叠 · 当前规则"));
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 3, TEXT);
        graphics.text(font, "启用连续重复聊天消息的折叠计数。", contentLeft(), y + 16, TEXT_DIM);
        int toggleX = contentRight() - 44;
        int toggleY = y + 2;
        drawToggle(graphics, toggleX, toggleY, 44, 16, ChatCleanerModule.instance().enabled(),
                isInside(mouseX, mouseY, toggleX, toggleY, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("已支持的规则", "SUPPORTED RULE / 已支持规则"), y);
        y += 20;
        y = wrapped(graphics,
                "相同文本的连续消息会显示次数，例如 (x2)。分隔线消息不会进入计数。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        y = wrapped(graphics,
                "当前 Fabric 实现不提供关键词过滤、消息删除策略编辑或服务器级消息管理。",
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int y = contentTop() + 20;
            if (isInside(event.x(), event.y(), contentRight() - 44, y + 2, 44, 16)) {
                ChatCleanerModule module = ChatCleanerModule.instance();
                module.setEnabled(!module.enabled());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}

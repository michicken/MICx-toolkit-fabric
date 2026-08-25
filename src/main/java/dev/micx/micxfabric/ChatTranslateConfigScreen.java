package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Chat translation configuration; the provider key remains built into the client. */
public final class ChatTranslateConfigScreen extends ModuleConfigScreen {
    private EditBox timeoutBox;

    public ChatTranslateConfigScreen(Screen parent) {
        super(parent, "ChatTranslate", "聊天翻译 · DeepSeek");
    }

    @Override
    protected void rebuildWidgets() {
        timeoutBox = new EditBox(font, 0, 0, 80, 20, Component.literal("timeout"));
        timeoutBox.setMaxLength(5);
        timeoutBox.setValue(Integer.toString(ChatTranslateModule.instance().timeoutMs()));
        timeoutBox.setBordered(true);
        addRenderableWidget(timeoutBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        graphics.text(font, "中文普通聊天在后台翻译后再发送。", contentLeft(), y + 17, TEXT_DIM);
        int toggleY = y + 1;
        int toggleX = contentRight() - 44;
        drawToggle(graphics, toggleX, toggleY, 44, 16, ChatTranslateModule.instance().enabled(),
                isInside(mouseX, mouseY, toggleX, toggleY, 44, 16));
        y += 38;
        y = wrapped(graphics, "API key 已内置在客户端，无需填写或保存。翻译失败、超时或空结果时只本地提示，原中文不会自动发送。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "PROVIDER / 翻译服务", y);
        y += 20;
        y = wrapped(graphics, "DeepSeek deepseek-v4-flash · Endpoint: https://api.deepseek.com/chat/completions",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "DELIVERY / 发送边界", y);
        y += 20;
        graphics.text(font, "Timeout (ms)", contentLeft(), y + 4, TEXT);
        timeoutBox.setX(contentRight() - 80);
        timeoutBox.setY(y);
        y += 32;
        y = wrapped(graphics, "范围沿用原 1.8.9：5000–60000 ms，默认 25000 ms。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        y = wrapped(graphics, "只处理中文普通消息；/ 命令、纯英文和超长文本不拦截。",
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int toggleY = contentY() + 20 + 1;
            if (isInside(event.x(), event.y(), contentRight() - 44, toggleY, 44, 16)) {
                ChatTranslateModule module = ChatTranslateModule.instance();
                module.setEnabled(!module.enabled());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        if (timeoutBox != null) {
            try {
                int timeout = Integer.parseInt(timeoutBox.getValue().trim());
                if (!ChatTranslateModule.instance().saveConfiguration(timeout)) {
                    setErrorMessage("无法保存超时配置，旧文件保持不变");
                    return;
                }
            } catch (NumberFormatException exception) {
                setErrorMessage("超时必须是数字，范围为 5000–60000 ms");
                return;
            }
        }
        super.saveAndClose();
    }
}

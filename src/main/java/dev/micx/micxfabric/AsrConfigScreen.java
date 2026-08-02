package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** ASR configuration with masked API key and configurable PTT key. */
public final class AsrConfigScreen extends ModuleConfigScreen {
    private EditBox apiKeyBox;
    private String draftApiKey;
    private int pttKey = GLFW.GLFW_KEY_V;
    private boolean listening;

    public AsrConfigScreen(Screen parent) {
        super(parent, "ASR", "语音输入 · StepFun Realtime");
        draftApiKey = AsrModule.instance().apiKey();
        pttKey = AsrModule.instance().pttKey();
    }

    @Override
    protected void rebuildWidgets() {
        apiKeyBox = new EditBox(font, 0, 0, 160, 20, Component.literal("API key"));
        apiKeyBox.setMaxLength(512);
        apiKeyBox.setValue(draftApiKey == null ? "" : draftApiKey);
        apiKeyBox.setBordered(true);
        apiKeyBox.addFormatter((value, cursor) -> net.minecraft.util.FormattedCharSequence.forward(mask(value), net.minecraft.network.chat.Style.EMPTY));
        addRenderableWidget(apiKeyBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        section(graphics, "CREDENTIALS / 凭据", y);
        y += 20;
        graphics.text(font, "API key", contentLeft(), y + 4, TEXT);
        apiKeyBox.setX(contentRight() - 160);
        apiKeyBox.setY(y);
        apiKeyBox.setWidth(160);
        y += 32;
        y = wrapped(graphics, "输入框默认掩码显示；保存时不会把完整 key 写入日志、聊天或错误提示。",
                contentLeft(), y, TEXT_FAINT, contentWidth());
        y += 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "PUSH TO TALK / 按键说话", y);
        y += 20;
        graphics.text(font, "PTT key", contentLeft(), y + 4, TEXT);
        String keyText = listening ? "按键或鼠标键 · ESC 取消" : new InputBinding(pttKey).label();
        int clearX = contentRight() - 52;
        int buttonX = clearX - 164;
        drawButton(graphics, keyText, buttonX, y, 160, 20,
                isInside(mouseX, mouseY, buttonX, y, 160, 20));
        drawButton(graphics, "Clear", clearX, y, 52, 20,
                isInside(mouseX, mouseY, clearX, y, 52, 20));
        y += 34;
        y = wrapped(graphics, "默认按键为 V。模型和 WebSocket endpoint 当前由模块固定，不在面板中伪装为可编辑设置。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "BOUNDARY / 边界", y);
        y += 20;
        y = wrapped(graphics, "识别结果按 Enter 发送，Escape 取消；连接、录音和最终确认超时沿用当前模块实现。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        setContentHeight(Math.max(y, 160) - contentTopForLayout());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening && event.button() != 0) {
            pttKey = -100 + event.button();
            AsrModule.instance().setPttKey(pttKey);
            listening = false;
            return true;
        }
        if (event.button() == 0) {
            int y = contentTopForLayout() + 20 + 32 + 14 + 14 + 20;
            int clearX = contentRight() - 52;
            int buttonX = clearX - 164;
            if (isInside(event.x(), event.y(), buttonX, y, 160, 20)) {
                listening = true;
                apiKeyBox.setFocused(false);
                return true;
            }
            if (isInside(event.x(), event.y(), clearX, y, 52, 20)) {
                pttKey = GLFW.GLFW_KEY_UNKNOWN;
                AsrModule.instance().setPttKey(pttKey);
                listening = false;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening) {
            if (event.isEscape()) {
                listening = false;
                return true;
            }
            if (event.key() != GLFW.GLFW_KEY_UNKNOWN && event.key() != GLFW.GLFW_KEY_ESCAPE) {
                pttKey = event.key();
                AsrModule.instance().setPttKey(pttKey);
                listening = false;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected void saveAndClose() {
        draftApiKey = apiKeyBox == null ? draftApiKey : apiKeyBox.getValue();
        if (!AsrModule.instance().saveConfiguration(draftApiKey, pttKey)) {
            setErrorMessage("无法保存 ASR 配置，旧文件保持不变");
            return;
        }
        super.saveAndClose();
    }

    private int contentTopForLayout() {
        return contentY();
    }
}

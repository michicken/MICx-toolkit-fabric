package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Functional KeyboardClicker settings for mode selection, delay, gate, and two bindings. */
public final class KeyboardClickerConfigScreen extends ModuleConfigScreen {
    private EditBox intervalBox;
    private boolean listeningToggle;
    private boolean listeningMode;

    public KeyboardClickerConfigScreen(Screen parent) {
        super(parent, "KeyboardClicker", "键盘连点 · 原生按键队列");
    }

    @Override
    protected void rebuildWidgets() {
        intervalBox = new EditBox(font, 0, 0, 64, 20, Component.literal("interval"));
        intervalBox.setMaxLength(3);
        intervalBox.setValue(Integer.toString(KeyboardClickerModule.instance().clickInterval()));
        intervalBox.setBordered(true);
        addRenderableWidget(intervalBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        KeyboardClickerModule module = KeyboardClickerModule.instance();
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        graphics.text(font, "只排队原版 hotbar KeyMapping click，不直接构造点击包。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "Right-click gate", contentLeft(), y + 4, TEXT);
        graphics.text(font, "启用后仅在按住原生右键时连点。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.rightClickTrigger(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "MODES / 模式", y);
        y += 20;
        graphics.text(font, "Current", contentLeft(), y + 4, TEXT);
        graphics.text(font, module.modeName(), contentLeft() + 70, y + 4, AMBER);
        graphics.text(font, "Interval (ms)", contentLeft() + 150, y + 4, TEXT);
        intervalBox.setX(contentRight() - 64);
        intervalBox.setY(y);
        y += 30;
        y = wrapped(graphics, "默认勾选 23 与 234；V 在已勾选模式间循环，` 切换连点开关。范围 40–100 ms。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "KEYS / 快捷键", y);
        y += 20;
        graphics.text(font, "Toggle", contentLeft(), y + 4, TEXT);
        drawButton(graphics, listeningToggle ? "按键..." : new InputBinding(module.toggleKey()).label(),
                contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 28;
        graphics.text(font, "Mode", contentLeft(), y + 4, TEXT);
        drawButton(graphics, listeningMode ? "按键..." : new InputBinding(module.modeKey()).label(),
                contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 34;
        y = wrapped(graphics, "按 1 可暂停，按 2/3/4 可恢复原版模式；所有操作受世界与 Screen 状态保护。",
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if ((listeningToggle || listeningMode) && event.button() != 0) {
            int code = -100 + event.button();
            boolean toggle = listeningToggle;
            listeningToggle = false;
            listeningMode = false;
            if (toggle) KeyboardClickerModule.instance().setToggleKey(code);
            else KeyboardClickerModule.instance().setModeKey(code);
            return true;
        }
        if (event.button() == 0) {
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                KeyboardClickerModule.instance().setEnabled(!KeyboardClickerModule.instance().enabled());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                KeyboardClickerModule module = KeyboardClickerModule.instance();
                module.setRightClickTrigger(!module.rightClickTrigger());
                return true;
            }
            y += 38 + 14 + 20 + 30 + 14 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 156, y, 156, 18)) {
                listeningToggle = true;
                listeningMode = false;
                return true;
            }
            y += 28;
            if (isInside(event.x(), event.y(), contentRight() - 156, y, 156, 18)) {
                listeningMode = true;
                listeningToggle = false;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningToggle || listeningMode) {
            if (event.isEscape()) {
                listeningToggle = false;
                listeningMode = false;
            } else if (listeningToggle) {
                KeyboardClickerModule.instance().setToggleKey(event.key());
                listeningToggle = false;
            } else {
                KeyboardClickerModule.instance().setModeKey(event.key());
                listeningMode = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void saveAndClose() {
        try {
            KeyboardClickerModule.instance().setClickInterval(Integer.parseInt(intervalBox.getValue().trim()));
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("间隔必须是 40–100 ms 的数字");
        }
    }
}

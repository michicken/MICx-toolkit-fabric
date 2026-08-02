package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Functional RightClicker settings without exposing a synthetic packet path. */
public final class RightClickerConfigScreen extends ModuleConfigScreen {
    private boolean listening;

    public RightClickerConfigScreen(Screen parent) {
        super(parent, "RightClicker", "自动右键 · 原版交互路径");
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        RightClickerModule module = RightClickerModule.instance();
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        graphics.text(font, "只清零原版使用冷却，交互仍由 Minecraft 原生路径处理。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "Active", contentLeft(), y + 4, TEXT);
        graphics.text(font, "按住原生右键时解除四 tick 客户端间隔。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.active(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "KEYBIND / 快捷键", y);
        y += 20;
        graphics.text(font, "Toggle", contentLeft(), y + 4, TEXT);
        String label = listening ? "按任意键或鼠标键 · ESC 取消" : new InputBinding(module.keyCode()).label();
        drawButton(graphics, label, contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 34;
        y = wrapped(graphics, "不会手工构造 C08/C07 网络包；禁用时不会留下按键或线程状态。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        setContentHeight(y - contentTop());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening && event.button() != 0) {
            RightClickerModule.instance().setKeyCode(-100 + event.button());
            listening = false;
            return true;
        }
        if (event.button() == 0) {
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                RightClickerModule.instance().setEnabled(!RightClickerModule.instance().enabled());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                RightClickerModule module = RightClickerModule.instance();
                module.setActive(!module.active());
                return true;
            }
            y += 38 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 156, y, 156, 18)) {
                listening = true;
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
            } else {
                RightClickerModule.instance().setKeyCode(event.key());
                listening = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }
}

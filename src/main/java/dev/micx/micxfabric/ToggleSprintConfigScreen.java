package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/** Functional ToggleSprint settings: module state, active state, and primary binding. */
public final class ToggleSprintConfigScreen extends ModuleConfigScreen {
    private boolean listening;

    public ToggleSprintConfigScreen(Screen parent) {
        super(parent, "ToggleSprint", "疾跑切换 · 客户端状态");
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        ToggleSprintModule module = ToggleSprintModule.instance();
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        graphics.text(font, "启用后沿用原版 sprint 条件与同步逻辑。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "Sprint lock", contentLeft(), y + 4, TEXT);
        graphics.text(font, "按主键切换锁定状态；关闭时恢复物理按键状态。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.active(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "HUD Text", contentLeft(), y + 4, TEXT);
        graphics.text(font, "屏幕左下角显示 [Sprint] / [Sprint OFF] 状态字样。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.hudEnabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "KEYBIND / 快捷键", y);
        y += 20;
        graphics.text(font, "Primary", contentLeft(), y + 4, TEXT);
        String label = listening ? "按任意键或鼠标键 · ESC 取消" : new InputBinding(module.keyCode()).label();
        drawButton(graphics, label, contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 34;
        y = wrapped(graphics, "绑定语义与原 1.8.9 一致：键盘使用 GLFW key code，鼠标使用 -100 + button。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        setContentHeight(y - contentTop());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening && event.button() != 0) {
            ToggleSprintModule.instance().setKeyCode(-100 + event.button());
            listening = false;
            return true;
        }
        if (event.button() == 0) {
            int firstToggleY = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, firstToggleY, 44, 16)) {
                ToggleSprintModule module = ToggleSprintModule.instance();
                module.setEnabled(!module.enabled());
                return true;
            }
            int activeY = firstToggleY + 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, activeY, 44, 16)) {
                ToggleSprintModule module = ToggleSprintModule.instance();
                module.setActive(!module.active());
                module.saveConfig();
                return true;
            }
            int hudY = activeY + 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, hudY, 44, 16)) {
                ToggleSprintModule module = ToggleSprintModule.instance();
                module.setHudEnabled(!module.hudEnabled());
                return true;
            }
            int bindY = hudY + 38 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 156, bindY, 156, 18)) {
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
                return true;
            }
            ToggleSprintModule.instance().setKeyCode(event.key());
            listening = false;
            return true;
        }
        return super.keyPressed(event);
    }

}

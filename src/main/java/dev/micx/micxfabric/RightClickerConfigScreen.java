package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * RightClicker CPS interval and keybind settings.
 * Mirrors Forge RightClickerPanelScreen slider behaviour (Min/Max with live info).
 */
public final class RightClickerConfigScreen extends ModuleConfigScreen {
    private EditBox minBox;
    private EditBox maxBox;
    private boolean listening;

    public RightClickerConfigScreen(Screen parent) {
        super(parent, "RightClicker", "自动右键 · 真实模拟 (20 CPS/每 tick 一发)");
    }

    @Override
    protected void rebuildWidgets() {
        minBox = new EditBox(font, 0, 0, 64, 20, Component.literal("cpsMin"));
        minBox.setMaxLength(2);
        minBox.setValue(Integer.toString(RightClickerModule.instance().getMinCps()));
        minBox.setBordered(true);
        addRenderableWidget(minBox);
        maxBox = new EditBox(font, 0, 0, 64, 20, Component.literal("cpsMax"));
        maxBox.setMaxLength(2);
        maxBox.setValue(Integer.toString(RightClickerModule.instance().getMaxCps()));
        maxBox.setBordered(true);
        addRenderableWidget(maxBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        RightClickerModule module = RightClickerModule.instance();
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        graphics.text(font, "真实注入 KeyMapping.click，每 tick 一发封顶；SkillCast 期间自动让路。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "Active", contentLeft(), y + 4, TEXT);
        graphics.text(font, "按住原生右键时按间隔自动点击。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.active(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "FIRE RATE / 射速", y);
        y += 20;
        graphics.text(font, "默认 20 = 扁平每 tick 一发；上下沿不同时随机取间隔（上沿超 20 实际封顶 20）", contentLeft(), y, TEXT_DIM);
        y += 14;
        graphics.text(font, "CPS Min", contentLeft(), y + 6, TEXT);
        minBox.setX(contentRight() - 140);
        minBox.setY(y);
        graphics.text(font, "CPS Max", contentLeft() + 90, y + 6, TEXT);
        maxBox.setX(contentRight() - 64);
        maxBox.setY(y);
        y += 30;
        graphics.text(font, "Current: " + module.getMinCps() + " - " + module.getMaxCps() + " CPS",
                contentLeft(), y, AMBER);
        y += 18;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "KEYBIND / 快捷键", y);
        y += 20;
        graphics.text(font, "Toggle", contentLeft(), y + 4, TEXT);
        String label = listening ? "按任意键或鼠标键 · ESC 取消" : new InputBinding(module.keyCode()).label();
        drawButton(graphics, label, contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 34;
        y = wrapped(graphics, "连点目标：" + module.useKeyStatus(),
                contentLeft(), y, AMBER, contentWidth());
        y += 4;
        y = wrapped(graphics, "按「使用键」当前绑定注入：改成左右键互换（左键=使用）后自动跟随改成左键连点，未绑定则不注入。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 4;
        y = wrapped(graphics, "区间可在 1-50 调节，Min>Max 会自动交换；与 SkillCast 互斥，不会叠加包。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening && event.button() != 0) {
            RightClickerModule.instance().setKeyCode(-100 + event.button());
            listening = false;
            return true;
        }
        if (event.button() == 0) {
            int statusY = contentTop() - scrollOffset() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, statusY, 44, 16)) {
                RightClickerModule.instance().setEnabled(!RightClickerModule.instance().enabled());
                return true;
            }
            statusY += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, statusY, 44, 16)) {
                RightClickerModule module = RightClickerModule.instance();
                module.setActive(!module.active());
                return true;
            }
            int cpsY = contentTop() - scrollOffset() + 21 + 38 + 38 + 14 + 20 + 14;
            // Keybind row is after CPS + info + line + section
            int keyY = cpsY + 30 + 18 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 156, keyY, 156, 18)) {
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

    @Override
    public void onClose() {
        try {
            int min = Integer.parseInt(minBox.getValue().trim());
            int max = Integer.parseInt(maxBox.getValue().trim());
            RightClickerModule.instance().setCpsRange(min, max);
            setErrorMessage(null);
        } catch (NumberFormatException ignored) {
        }
        super.onClose();
    }

    @Override
    protected void saveAndClose() {
        try {
            int min = Integer.parseInt(minBox.getValue().trim());
            int max = Integer.parseInt(maxBox.getValue().trim());
            RightClickerModule.instance().setCpsRange(min, max);
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("CPS 必须是 1–50 的数字");
        }
    }
}

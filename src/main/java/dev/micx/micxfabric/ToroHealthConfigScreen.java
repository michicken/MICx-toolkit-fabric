package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Complete migrated ToroHealth configuration surface. */
public final class ToroHealthConfigScreen extends ModuleConfigScreen {
    private static final String[] MODE_LABELS = {"Hearts", "Numeric", "Off"};
    private static final String[] POSITION_LABELS = {
            "Top Left", "Top Center", "Top Right", "Bottom Left", "Bottom Right", "Custom"
    };
    private static final int[] COLORS = {0xFF0000, 0x00FF00, 0xFFFF00, 0x0000FF, 0xFF5500, 0x9900FF};
    private static final String[] COLOR_LABELS = {"Red", "Green", "Yellow", "Blue", "Orange", "Purple"};

    private EditBox xBox;
    private EditBox yBox;

    public ToroHealthConfigScreen(Screen parent) {
        super(parent, "ToroHealth", "准心目标血量 · Hearts / Numeric");
    }

    @Override
    protected void rebuildWidgets() {
        ToroHealthModule module = ToroHealthModule.instance();
        xBox = new EditBox(font, 0, 0, 72, 20, Component.literal("X"));
        xBox.setMaxLength(7);
        xBox.setValue(Integer.toString(module.displayX()));
        yBox = new EditBox(font, 0, 0, 72, 20, Component.literal("Y"));
        yBox.setMaxLength(7);
        yBox.setValue(Integer.toString(module.displayY()));
        addRenderableWidget(xBox);
        addRenderableWidget(yBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        ToroHealthModule module = ToroHealthModule.instance();
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        graphics.text(font, "启用准心目标血量 HUD。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "Overlay", contentLeft(), y + 4, TEXT);
        graphics.text(font, "关闭后保留配置，但不绘制目标面板。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.overlayEnabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "DISPLAY / 显示", y);
        y += 20;
        graphics.text(font, "Mode", contentLeft(), y + 4, TEXT);
        graphics.text(font, "原 Forge 的 Hearts / Numeric / Off 三种模式。", contentLeft(), y + 17, TEXT_DIM);
        drawButton(graphics, modeLabel(module.displayMode()), contentRight() - 112, y + 1, 112, 18,
                isInside(mouseX, mouseY, contentRight() - 112, y + 1, 112, 18));
        y += 38;
        graphics.text(font, "Position", contentLeft(), y + 4, TEXT);
        graphics.text(font, "Custom 时使用下面的屏幕像素坐标。", contentLeft(), y + 17, TEXT_DIM);
        drawButton(graphics, positionLabel(module.displayPosition()), contentRight() - 112, y + 1, 112, 18,
                isInside(mouseX, mouseY, contentRight() - 112, y + 1, 112, 18));
        y += 38;
        graphics.text(font, "X", contentLeft(), y + 4, TEXT);
        xBox.setX(contentRight() - 72);
        xBox.setY(y);
        y += 28;
        graphics.text(font, "Y", contentLeft(), y + 4, TEXT);
        yBox.setX(contentRight() - 72);
        yBox.setY(y);
        y += 34;
        y = wrapped(graphics, "位置范围为 -20000 到 20000；非 Custom 位置会忽略 X/Y。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 14;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "TIMING / 时长", y);
        y += 20;
        graphics.text(font, "Hide delay", contentLeft(), y + 4, TEXT);
        graphics.text(font, module.hideDelayMs() + " ms", contentRight() - 72, y + 4, TEXT_DIM);
        drawButton(graphics, "−", contentRight() - 132, y, 24, 18,
                isInside(mouseX, mouseY, contentRight() - 132, y, 24, 18));
        drawButton(graphics, "+", contentRight() - 28, y, 24, 18,
                isInside(mouseX, mouseY, contentRight() - 28, y, 24, 18));
        y += 28;
        y = wrapped(graphics, "准心离开目标后保留显示 50–5000 ms。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 14;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "DAMAGE PARTICLES / 伤害粒子", y);
        y += 20;
        y = wrapped(graphics, "原 Forge 字段已保留并兼容配置，但 26.2 粒子注入尚未迁移；当前没有可点击的假开关。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 6;
        graphics.text(font, "Damage color", contentLeft(), y + 4, TEXT);
        drawButton(graphics, colorLabel(module.damageColor()), contentRight() - 112, y, 112, 18,
                isInside(mouseX, mouseY, contentRight() - 112, y, 112, 18));
        y += 28;
        graphics.text(font, "Heal color", contentLeft(), y + 4, TEXT);
        drawButton(graphics, colorLabel(module.healColor()), contentRight() - 112, y, 112, 18,
                isInside(mouseX, mouseY, contentRight() - 112, y, 112, 18));
        y += 28;
        setContentHeight(y - contentTop() + scrollOffset());
    }

    private int wrapped(GuiGraphicsExtractor graphics, String text) {
        return wrapped(graphics, text, contentLeft(), 0, TEXT_DIM, contentWidth());
    }

    private String modeLabel(int mode) {
        return MODE_LABELS[Math.max(0, Math.min(MODE_LABELS.length - 1, mode))];
    }

    private String positionLabel(int position) {
        return POSITION_LABELS[Math.max(0, Math.min(POSITION_LABELS.length - 1, position))];
    }

    private String colorLabel(int color) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == (color & 0xFFFFFF)) return COLOR_LABELS[i];
        }
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private int nextColor(int color) {
        int normalized = color & 0xFFFFFF;
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == normalized) return COLORS[(i + 1) % COLORS.length];
        }
        return COLORS[0];
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            ToroHealthModule module = ToroHealthModule.instance();
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setEnabled(!module.enabled());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setOverlayEnabled(!module.overlayEnabled());
                return true;
            }
            y += 38 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 112, y + 1, 112, 18)) {
                module.setDisplayMode((module.displayMode() + 1) % MODE_LABELS.length);
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 112, y + 1, 112, 18)) {
                module.setDisplayPosition((module.displayPosition() + 1) % POSITION_LABELS.length);
                return true;
            }
            y += 38 + 28 + 34 + 14 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 132, y, 24, 18)) {
                module.setHideDelayMs(module.hideDelayMs() - 50);
                return true;
            }
            if (isInside(event.x(), event.y(), contentRight() - 28, y, 24, 18)) {
                module.setHideDelayMs(module.hideDelayMs() + 50);
                return true;
            }
            y += 28 + 14 + 14 + 20 + 20 + 6;
            if (isInside(event.x(), event.y(), contentRight() - 112, y, 112, 18)) {
                module.setDamageColor(nextColor(module.damageColor()));
                return true;
            }
            y += 28;
            if (isInside(event.x(), event.y(), contentRight() - 112, y, 112, 18)) {
                module.setHealColor(nextColor(module.healColor()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        try {
            ToroHealthModule module = ToroHealthModule.instance();
            module.setDisplayX(Integer.parseInt(xBox.getValue().trim()));
            module.setDisplayY(Integer.parseInt(yBox.getValue().trim()));
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("X/Y 必须是 -20000 到 20000 的整数");
        }
    }
}

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
        super(parent, UiText.shown("伤害数字", "ToroHealth"), UiText.shown("ToroHealth · 准心目标血量", "准心目标血量 · Hearts / Numeric"));
    }

    @Override
    protected void rebuildWidgets() {
        ToroHealthModule module = ToroHealthModule.instance();
        xBox = new EditBox(font, 0, 0, 72, 20, Component.literal(UiText.shown("水平位置 X", "X")));
        xBox.setMaxLength(7);
        xBox.setValue(Integer.toString(module.displayX()));
        yBox = new EditBox(font, 0, 0, 72, 20, Component.literal(UiText.shown("垂直位置 Y", "Y")));
        yBox.setMaxLength(7);
        yBox.setValue(Integer.toString(module.displayY()));
        addRenderableWidget(xBox);
        addRenderableWidget(yBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        ToroHealthModule module = ToroHealthModule.instance();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("显示准心目标的血量和吸收盾。", "启用准心目标血量 HUD。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("显示方式", "Overlay"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "关闭后保留配置，但不绘制目标面板。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.overlayEnabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("显示", "DISPLAY / 显示"), y);
        y += 20;
        graphics.text(font, UiText.shown("模式", "Mode"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("沿用原 Forge 的三种模式：心形 / 数字 / 关闭。", "原 Forge 的 Hearts / Numeric / Off 三种模式。"), contentLeft(), y + 17, TEXT_DIM);
        drawButton(graphics, modeLabel(module.displayMode()), contentRight() - 112, y + 1, 112, 18,
                isInside(mouseX, mouseY, contentRight() - 112, y + 1, 112, 18));
        y += 38;
        graphics.text(font, UiText.shown("显示位置", "Position"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "Custom 时使用下面的屏幕像素坐标。", contentLeft(), y + 17, TEXT_DIM);
        drawButton(graphics, positionLabel(module.displayPosition()), contentRight() - 112, y + 1, 112, 18,
                isInside(mouseX, mouseY, contentRight() - 112, y + 1, 112, 18));
        y += 38;
        graphics.text(font, UiText.shown("水平位置 X", "X"), contentLeft(), y + 4, TEXT);
        xBox.setX(contentRight() - 72);
        xBox.setY(y);
        y += 28;
        graphics.text(font, UiText.shown("垂直位置 Y", "Y"), contentLeft(), y + 4, TEXT);
        yBox.setX(contentRight() - 72);
        yBox.setY(y);
        y += 34;
        y = wrapped(graphics, UiText.shown("位置可填 -20000 到 20000。选了预设位置时，X / Y 会被忽略。", "位置范围为 -20000 到 20000；非 Custom 位置会忽略 X/Y。"),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 14;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("时长", "TIMING / 时长"), y);
        y += 20;
        graphics.text(font, UiText.shown("消失延迟", "Hide delay"), contentLeft(), y + 4, TEXT);
        graphics.text(font, module.hideDelayMs() + " ms", contentRight() - 72, y + 4, TEXT_DIM);
        drawButton(graphics, "−", contentRight() - 132, y, 24, 18,
                isInside(mouseX, mouseY, contentRight() - 132, y, 24, 18));
        drawButton(graphics, "+", contentRight() - 28, y, 24, 18,
                isInside(mouseX, mouseY, contentRight() - 28, y, 24, 18));
        y += 28;
        y = wrapped(graphics, UiText.shown("准心移开之后，数字还会保留 50–5000 毫秒。", "准心离开目标后保留显示 50–5000 ms。"),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 14;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("伤害粒子", "DAMAGE PARTICLES / 伤害粒子"), y);
        y += 20;
        y = wrapped(graphics, UiText.shown("原 Forge 的伤害粒子字段还留着、配置也兼容，但 26.2 上还没接上，所以这里没有对应的开关。", "原 Forge 字段已保留并兼容配置，但 26.2 粒子注入尚未迁移；当前没有可点击的假开关。"),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 6;
        graphics.text(font, UiText.shown("伤害数字颜色", "Damage color"), contentLeft(), y + 4, TEXT);
        drawButton(graphics, colorLabel(module.damageColor()), contentRight() - 112, y, 112, 18,
                isInside(mouseX, mouseY, contentRight() - 112, y, 112, 18));
        y += 28;
        graphics.text(font, UiText.shown("治疗数字颜色", "Heal color"), contentLeft(), y + 4, TEXT);
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

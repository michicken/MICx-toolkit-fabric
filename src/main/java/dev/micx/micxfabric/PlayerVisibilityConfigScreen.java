package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** PlayerVisibility settings for the migrated hide/opacity/range behavior. */
public final class PlayerVisibilityConfigScreen extends ModuleConfigScreen {
    private EditBox rangeBox;
    private EditBox opacityBox;
    private boolean listening;

    public PlayerVisibilityConfigScreen(Screen parent) {
        super(parent, "PlayerVisibility", "玩家隐身 · 隐藏或透明");
    }

    @Override
    protected void rebuildWidgets() {
        PlayerVisibilityModule module = PlayerVisibilityModule.instance();
        rangeBox = new EditBox(font, 0, 0, 68, 20, Component.literal("range"));
        rangeBox.setMaxLength(6);
        rangeBox.setValue(Float.toString(module.range()));
        rangeBox.setBordered(true);
        opacityBox = new EditBox(font, 0, 0, 68, 20, Component.literal("opacity"));
        opacityBox.setMaxLength(6);
        opacityBox.setValue(Float.toString(module.opacity()));
        opacityBox.setBordered(true);
        addRenderableWidget(rangeBox);
        addRenderableWidget(opacityBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        PlayerVisibilityModule module = PlayerVisibilityModule.instance();
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        graphics.text(font, "渲染钩子只作用于其他玩家；自己和睡觉玩家保留。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "Active", contentLeft(), y + 4, TEXT);
        graphics.text(font, "主键只切换当前生效状态，不改变模块注册。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.active(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "Hide mode", contentLeft(), y + 4, TEXT);
        graphics.text(font, "隐藏模式取消附近玩家和其乘坐实体的渲染。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.hideMode(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "RANGE / 范围", y);
        y += 20;
        graphics.text(font, "Blocks", contentLeft(), y + 4, TEXT);
        rangeBox.setX(contentRight() - 68);
        rangeBox.setY(y);
        y += 28;
        graphics.text(font, "Opacity", contentLeft(), y + 4, TEXT);
        opacityBox.setX(contentRight() - 68);
        opacityBox.setY(y);
        y += 34;
        y = wrapped(graphics, "范围 0.5–64 格；透明度 0.05–1.0。旧配置中的 rangeSq 会按平方根转换。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "KEYBIND / 快捷键", y);
        y += 20;
        String label = listening ? "按任意键或鼠标键 · ESC 取消" : new InputBinding(module.keyCode()).label();
        drawButton(graphics, label, contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 32;
        y = wrapped(graphics, "Fabric 透明模式使用实体 extraction state 的 translucent render type，不修改全局 OpenGL 状态。",
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening && event.button() != 0) {
            PlayerVisibilityModule.instance().setKeyCode(-100 + event.button());
            listening = false;
            return true;
        }
        if (event.button() == 0) {
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                PlayerVisibilityModule.instance().setEnabled(!PlayerVisibilityModule.instance().enabled());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                PlayerVisibilityModule module = PlayerVisibilityModule.instance();
                module.setActive(!module.active());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                PlayerVisibilityModule module = PlayerVisibilityModule.instance();
                module.setHideMode(!module.hideMode());
                return true;
            }
            y += 38 + 14 + 20 + 28 + 34 + 14 + 14 + 20;
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
                PlayerVisibilityModule.instance().setKeyCode(event.key());
                listening = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void saveAndClose() {
        try {
            PlayerVisibilityModule module = PlayerVisibilityModule.instance();
            module.setRange(Float.parseFloat(rangeBox.getValue().trim()));
            module.setOpacity(Float.parseFloat(opacityBox.getValue().trim()));
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("范围必须是 0.5–64，透明度必须是 0.05–1.0");
        }
    }
}

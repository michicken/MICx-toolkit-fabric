package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** PlayerOutlineESP controls for the native 26.2 outline path. */
public final class PlayerOutlineEspConfigScreen extends ModuleConfigScreen {
    private EditBox rangeBox;

    public PlayerOutlineEspConfigScreen(Screen parent) {
        super(parent, UiText.shown("玩家轮廓", "PlayerOutlineESP"), UiText.shown("PlayerOutlineESP · 26.2 原生 outline", "玩家轮廓 · native outline phase"));
    }

    @Override
    protected void rebuildWidgets() {
        PlayerOutlineEspModule module = PlayerOutlineEspModule.instance();
        rangeBox = new EditBox(font, 0, 0, 72, 20, Component.literal("range"));
        rangeBox.setMaxLength(6);
        rangeBox.setValue(Float.toString(module.range()));
        addRenderableWidget(rangeBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        PlayerOutlineEspModule module = PlayerOutlineEspModule.instance();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("把其他玩家设成绿色轮廓，绘制交给 26.2 原生的 outline 阶段。", "为其他玩家设置绿色 outlineColor，交给 26.2 原生 outline phase。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("实际生效", "Active"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("距离之外的玩家不会进入轮廓绘制状态。", "范围外玩家不进入 outline state。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.active(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("生效范围", "RANGE / 范围"), y);
        y += 20;
        graphics.text(font, UiText.shown("生效距离", "Range"), contentLeft(), y + 4, TEXT);
        rangeBox.setX(contentRight() - 72);
        rangeBox.setY(y);
        y += 28;
        y = wrapped(graphics, UiText.shown("生效距离 8–256 格，范围外的玩家不进入轮廓绘制。轮廓粗细用的是 26.2 原生 fixed outline，暂时改不了，所以这里没有粗细滑块。", "范围 8–256 格。原 Forge 的投影厚度 1–5px × 1.20 尚未替换原生固定 outline shader，因此本页不提供假厚度滑块。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        y += 14;
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            PlayerOutlineEspModule module = PlayerOutlineEspModule.instance();
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setEnabled(!module.enabled());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setActive(!module.active());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        try {
            PlayerOutlineEspModule.instance().setRange(Float.parseFloat(rangeBox.getValue().trim()));
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("Range 必须是 8–256");
        }
    }
}

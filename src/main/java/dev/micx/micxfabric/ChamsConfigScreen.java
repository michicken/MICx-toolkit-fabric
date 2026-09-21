package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Configuration page for the textured model Chams module. */
public final class ChamsConfigScreen extends ModuleConfigScreen {
    private EditBox rangeBox;

    public ChamsConfigScreen(Screen parent) {
        super(parent, UiText.shown("模型透视", "Chams"), UiText.shown("Chams · 只画被遮挡的目标", "原贴图模型透墙 · 仅遮挡目标"));
    }

    @Override
    protected void rebuildWidgets() {
        rangeBox = new EditBox(font, 0, 0, 72, 20, Component.literal("range"));
        rangeBox.setMaxLength(3);
        rangeBox.setValue(Integer.toString(ChamsModule.instance().range()));
        addRenderableWidget(rangeBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        ChamsModule module = ChamsModule.instance();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "只替换被方块遮挡目标的 vanilla 模型材质；ESP 线框独立控制。",
                contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 42;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("生效范围", "RANGE / 范围"), y);
        y += 20;
        graphics.text(font, UiText.shown("生效距离", "Range"), contentLeft(), y + 4, TEXT);
        rangeBox.setX(contentRight() - 72);
        rangeBox.setY(y);
        y += 32;
        y = wrapped(graphics, UiText.shown("生效距离 8–128 格。看得见的怪仍然按原版渲染；只有从你的眼睛到怪物中心被方块挡住时，才换成模型透视。", "范围 8–128 格。可见实体保持原版渲染；只有玩家眼睛到实体中心的方块射线命中时才启用模型 Chams。"),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("使用限制", "BOUNDARY / 边界"), y);
        y += 20;
        y = wrapped(graphics, UiText.shown("它画的是模型而不是方框，和 ESP 的线框各管各的。关闭模块、断线或换世界时会清掉已经记录的遮挡。", "不复用 ESP 的线框管线，不绘制纯色方框；模块关闭、断开或切世界时会清空遮挡缓存。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            ChamsModule module = ChamsModule.instance();
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setEnabled(!module.enabled());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        try {
            ChamsModule.instance().setRange(Integer.parseInt(rangeBox.getValue().trim()));
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("Range 必须是 8–128 的整数");
        }
    }
}

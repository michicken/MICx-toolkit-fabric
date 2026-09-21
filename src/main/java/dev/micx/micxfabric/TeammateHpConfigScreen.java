package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** TeammateHP settings matching the Forge overlay fields and bounds. */
public final class TeammateHpConfigScreen extends ModuleConfigScreen {
    private EditBox widthBox;
    private EditBox alphaBox;
    private boolean listening;

    public TeammateHpConfigScreen(Screen parent) {
        super(parent, UiText.shown("队友血量", "TeammateHP"), UiText.shown("TeammateHP · 最多显示四名玩家", "队友血量卡片 · 最多显示四名玩家"));
    }

    @Override
    protected void rebuildWidgets() {
        TeammateHpModule module = TeammateHpModule.instance();
        widthBox = new EditBox(font, 0, 0, 72, 20, Component.literal("card width"));
        widthBox.setMaxLength(3);
        widthBox.setValue(Integer.toString(module.cardWidth()));
        alphaBox = new EditBox(font, 0, 0, 72, 20, Component.literal("background alpha"));
        alphaBox.setMaxLength(3);
        alphaBox.setValue(Integer.toString(module.bgAlpha()));
        addRenderableWidget(widthBox);
        addRenderableWidget(alphaBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        TeammateHpModule module = TeammateHpModule.instance();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("注册队友卡片 HUD 和 H 键开关。", "注册队友卡片 HUD 和 H 键切换。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("显示卡片", "Show Cards"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "主键只切换显示状态，不卸载模块。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.active(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("游戏内叠层", "In-Game overlay"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("在游戏 HUD 上画出队友卡片。", "是否在游戏 HUD 上绘制队友卡片。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.overlayEnabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("包含隐身队友", "Show Hidden"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("连被客户端标记为隐身的队友也算进去。", "包含被客户端标记为 invisible 的队友。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.showHidden(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("显示距离", "Show Distance"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("在卡片右侧显示和你自己的距离。", "在卡片右侧显示与自己的距离。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.showDistance(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("救援计时", "Revive Timer"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "B=读倒地 holo 救援计时（服务端真值）；A=25s 本地估算。", contentLeft(), y + 17, TEXT_DIM);
        drawButton(graphics, module.isReviveHoloB() ? "B（读 hologram）" : "A（25s 本地）",
                contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 38;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("卡片", "CARD / 卡片"), y);
        y += 20;
        graphics.text(font, UiText.shown("卡片宽度", "Card width"), contentLeft(), y + 4, TEXT);
        widthBox.setX(contentRight() - 72);
        widthBox.setY(y);
        y += 28;
        graphics.text(font, UiText.shown("背景不透明度", "Background alpha"), contentLeft(), y + 4, TEXT);
        alphaBox.setX(contentRight() - 72);
        alphaBox.setY(y);
        y += 34;
        y = wrapped(graphics, UiText.shown("卡片宽度 100–400，背景不透明度 0–200。X / Y、缩放和 H 键仍用默认值，本页不提供。", "Card width 100–400；背景 alpha 0–200。X/Y、缩放和 H 键在本页以外保留默认布局。"),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 14;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("快捷键", "KEYBIND / 快捷键"), y);
        y += 20;
        String keyLabel = listening ? "按任意键或鼠标键 · ESC 取消" : new InputBinding(module.keyCode()).label();
        drawButton(graphics, keyLabel, contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 32;
        y = wrapped(graphics, UiText.shown("默认是 H 键。名单优先用当前已加载的其他玩家，最多同时显示四张卡片。", "默认 H 键；名单优先使用当前加载的其他玩家，最多固定显示四张卡片。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        y += 14;
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening && event.button() != 0) {
            TeammateHpModule.instance().setKeyCode(-100 + event.button());
            listening = false;
            return true;
        }
        if (event.button() == 0) {
            TeammateHpModule module = TeammateHpModule.instance();
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
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setOverlayEnabled(!module.overlayEnabled());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setShowHidden(!module.showHidden());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setShowDistance(!module.showDistance());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 156, y, 156, 18)) {
                module.setReviveHoloB(!module.isReviveHoloB());
                return true;
            }
            y += 14 + 20 + 28 + 34 + 14 + 14 + 20;
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
                TeammateHpModule.instance().setKeyCode(event.key());
                listening = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void saveAndClose() {
        try {
            TeammateHpModule module = TeammateHpModule.instance();
            module.setCardWidth(Integer.parseInt(widthBox.getValue().trim()));
            module.setBgAlpha(Integer.parseInt(alphaBox.getValue().trim()));
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("Card width 必须是 100–400，alpha 必须是 0–200");
        }
    }
}

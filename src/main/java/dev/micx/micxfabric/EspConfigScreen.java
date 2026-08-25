package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** ESP controls for range, opacity and Forge-compatible automatic gating. */
public final class EspConfigScreen extends ModuleConfigScreen {
    private EditBox rangeBox;
    private EditBox alphaBox;
    private EditBox gateRoundBox;
    private EditBox gateMobsBox;

    public EspConfigScreen(Screen parent) {
        super(parent, "ESP", "线框透视 · 26.2 submit pipeline");
    }

    @Override
    protected void rebuildWidgets() {
        EspModule module = EspModule.instance();
        rangeBox = box("range", Float.toString(module.range()), 6);
        alphaBox = box("opacity", Float.toString(module.alpha() * 100.0f), 6);
        gateRoundBox = box("gate round", Integer.toString(module.gateRound()), 4);
        gateMobsBox = box("gate mobs", Integer.toString(module.gateLowMobs()), 4);
        addRenderableWidget(rangeBox);
        addRenderableWidget(alphaBox);
        addRenderableWidget(gateRoundBox);
        addRenderableWidget(gateMobsBox);
    }

    private EditBox box(String name, String value, int length) {
        EditBox box = new EditBox(font, 0, 0, 72, 20, Component.literal(name));
        box.setMaxLength(length);
        box.setValue(value);
        return box;
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        EspModule module = EspModule.instance();
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        graphics.text(font, "提交非玩家实体线框；不会修改全局 OpenGL 状态。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "Active", contentLeft(), y + 4, TEXT);
        graphics.text(font, "关闭时保留模块配置但不提交线框。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.active(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "RANGE / 范围", y);
        y += 20;
        graphics.text(font, "Range", contentLeft(), y + 4, TEXT);
        rangeBox.setX(contentRight() - 72);
        rangeBox.setY(y);
        y += 28;
        graphics.text(font, "Opacity %", contentLeft(), y + 4, TEXT);
        alphaBox.setX(contentRight() - 72);
        alphaBox.setY(y);
        y += 34;
        y = wrapped(graphics, "范围 8–256 格；透明度 5–100%。普通实体使用默认红色，Golem/Wither 使用独立颜色。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 14;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "AUTO GATE / 自动门控", y);
        y += 20;
        graphics.text(font, "Auto Gate", contentLeft(), y + 4, TEXT);
        graphics.text(font, "Round 达到阈值且 Left 不低于门槛时暂停普通 ESP。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.autoGate(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, "Gate Round", contentLeft(), y + 4, TEXT);
        gateRoundBox.setX(contentRight() - 72);
        gateRoundBox.setY(y);
        y += 28;
        graphics.text(font, "Show Under", contentLeft(), y + 4, TEXT);
        gateMobsBox.setX(contentRight() - 72);
        gateMobsBox.setY(y);
        y += 34;
        y = wrapped(graphics, "Gate Round 10–110；Show Under 1–100。TOO/Giant 优先目标和完整 TeamSync white target 仍待后续接入。",
                contentLeft(), y, TEXT_FAINT, contentWidth());
        y += 14;
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            EspModule module = EspModule.instance();
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
            y += 38 + 14 + 20 + 28 + 34 + 14 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setAutoGate(!module.autoGate());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        try {
            EspModule module = EspModule.instance();
            module.setRange(Float.parseFloat(rangeBox.getValue().trim()));
            module.setAlpha(Float.parseFloat(alphaBox.getValue().trim()) / 100.0f);
            module.setGateRound(Integer.parseInt(gateRoundBox.getValue().trim()));
            module.setGateLowMobs(Integer.parseInt(gateMobsBox.getValue().trim()));
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("Range/Opacity/Gate 必须是有效数字");
        }
    }
}

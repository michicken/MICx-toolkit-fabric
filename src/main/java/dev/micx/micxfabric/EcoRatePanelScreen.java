package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.EditBox;

public final class EcoRatePanelScreen extends ModuleConfigScreen {
    private final EcoRateModule mod;
    private EditBox intervalBox;
    private EditBox durationBox;

    public EcoRatePanelScreen(Screen parent) {
        super(parent, "EcoRate", "经济速率闪烁 · 每 2 分钟纯增长");
        this.mod = EcoRateModule.instance();
    }

    @Override
    protected void rebuildWidgets() {
        EcoRateConfig c = EcoRateModule.cfg();
        intervalBox = new EditBox(font, 0, 0, 72, 20, Component.literal("interval"));
        intervalBox.setMaxLength(4);
        intervalBox.setValue(Integer.toString(c.flashIntervalSec));
        addRenderableWidget(intervalBox);
        durationBox = new EditBox(font, 0, 0, 72, 20, Component.literal("duration"));
        durationBox.setMaxLength(4);
        durationBox.setValue(Integer.toString(c.flashDurationSec));
        addRenderableWidget(durationBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
        section(g, "MODULE / 模块", y); y+=20;
        g.text(font, "Enable", contentLeft(), y+4, TEXT);
        g.text(font, "右侧经济表周期性把金币切为每2分钟纯增长速率（绿字）。", contentLeft(), y+17, TEXT_DIM);
        drawToggle(g, contentRight()-44, y+1, 44, 16, mod.enabled(), isInside(mouseX, mouseY, contentRight()-44, y+1, 44, 16));
        y+=38;
        // numeric rows
        section(g, "FLASH / 闪烁节奏", y); y+=20;
        g.text(font, "Flash Interval (s)", contentLeft(), y+4, TEXT);
        g.text(font, "每隔多少秒闪一次速率（2-30）。", contentLeft(), y+17, TEXT_DIM);
        intervalBox.setX(contentRight()-72); intervalBox.setY(y);
        y+=34;
        g.text(font, "Flash Duration (s)", contentLeft(), y+4, TEXT);
        g.text(font, "速率显示多久后切回金币（1-10，且 < 间隔）。", contentLeft(), y+17, TEXT_DIM);
        durationBox.setX(contentRight()-72); durationBox.setY(y);
        y+=34;
        g.fill(contentLeft(), y, contentRight(), y+1, LINE); y+=14;
        info(g, "速率 = 过去2分钟纯增长（买装备不影响）：自己按聊天 +Gold 事件，队友按记分板正增量；数值每10秒刷新一次。模块关闭时后台采集继续，中途开启立即有数据。", y);
        y+=46;
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button()==0 && isInside(event.x(), event.y(), contentRight()-44, contentTop()-scrollOffset()+21, 44, 16)) {
            ModuleRuntime.setEnabled(mod.id(), !mod.enabled());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        try {
            int iv = Math.max(2, Math.min(30, Integer.parseInt(intervalBox.getValue().trim())));
            int du = Math.max(1, Math.min(10, Integer.parseInt(durationBox.getValue().trim())));
            EcoRateConfig c = EcoRateModule.cfg();
            c.flashIntervalSec = iv;
            c.flashDurationSec = Math.min(du, iv-1);
            if (c.flashDurationSec < 1) c.flashDurationSec = 1;
            c.save();
            super.saveAndClose();
        } catch (NumberFormatException e) {
            setErrorMessage("间隔/时长必须是数字（间隔2-30，时长1-10且<间隔）");
        }
    }
}

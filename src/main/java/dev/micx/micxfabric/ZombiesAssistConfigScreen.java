package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Configures the validated Zombies scoreboard/HUD increment. */
public final class ZombiesAssistConfigScreen extends ModuleConfigScreen {
    private EditBox yBox;
    private EditBox scaleBox;

    public ZombiesAssistConfigScreen(Screen parent) {
        super(parent, UiText.shown("僵尸助手", "ZombiesAssist"), UiText.shown("ZombiesAssist · 波次 HUD 与记分板", "僵尸波次 HUD · scoreboard tracker"));
    }

    @Override
    protected void rebuildWidgets() {
        ZombiesAssistModule module = ZombiesAssistModule.instance();
        yBox = new EditBox(font, 0, 0, 72, 20, Component.literal("top HUD Y"));
        yBox.setMaxLength(6);
        yBox.setValue(Integer.toString(module.topHudY()));
        scaleBox = new EditBox(font, 0, 0, 72, 20, Component.literal("top HUD scale"));
        scaleBox.setMaxLength(6);
        scaleBox.setValue(Float.toString(module.topHudScale()));
        addRenderableWidget(yBox);
        addRenderableWidget(scaleBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        ZombiesAssistModule module = ZombiesAssistModule.instance();
        ZombiesTracker tracker = ZombiesTracker.instance();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("打开 scoreboard 追踪和已经迁移过来的那部分 HUD。", "启用 scoreboard tracker 与当前已迁移的 HUD。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("HUD 叠层", "HUD Overlay"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("显示 Round / Left / AA 那一行状态。", "显示 Round / Left / AA 状态行。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.overlayEnabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("剩余怪数", "Mobs count"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("显示从 scoreboard 解析出来的 Left（剩余怪数）。", "显示当前 scoreboard 中解析出的 Left 数值。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.showMobs(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("HUD 位置", "HUD POSITION / 位置"), y);
        y += 20;
        graphics.text(font, UiText.shown("顶部 Y 坐标", "Top Y"), contentLeft(), y + 4, TEXT);
        yBox.setX(contentRight() - 72);
        yBox.setY(y);
        y += 28;
        graphics.text(font, UiText.shown("缩放", "Scale"), contentLeft(), y + 4, TEXT);
        scaleBox.setX(contentRight() - 72);
        scaleBox.setY(y);
        y += 34;
        y = wrapped(graphics, UiText.shown("Y 可填 0–20000，缩放可填 0.5–2.0。X 偏移仍然只能改配置文件。", "Y 范围 0–20000；缩放范围 0.5–2.0。X 偏移仍可通过配置文件保留。"),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 14;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("追踪范围", "TRACKER / 追踪边界"), y);
        y += 20;
        y = wrapped(graphics, tracker.isInZombies()
                        ? "当前识别到 Zombies scoreboard：Round=" + tracker.round()
                        + "，Left=" + (tracker.zombiesLeft() < 0 ? "?" : tracker.zombiesLeft())
                        : "当前没有识别到 Zombies scoreboard；HUD 会显示 No ZB。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 6;
        y = wrapped(graphics, UiText.shown("目前接上的有：scoreboard 的波次 / 剩余数、AA 状态、Fast Revive 延迟。还有一批（FR、Power-up、TOO、LS、声音与 title 的完整对应、自动聊天、世界光柱）还没在 Prism 实例上实机验收，暂时标为 PORTING。", "已接入 scoreboard 波次/剩余数、AA 和 Fast Revive latency 代码路径；FR、Power-up、TOO、LS、声音/title 完整相关性、自动聊天和世界 beam 尚未完成 Prism 实机验收，继续标记 PORTING。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        y += 14;
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            ZombiesAssistModule module = ZombiesAssistModule.instance();
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                ModuleRuntime.setEnabled(module.id(), !module.enabled());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setOverlayEnabled(!module.overlayEnabled());
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setShowMobs(!module.showMobs());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        try {
            ZombiesAssistModule module = ZombiesAssistModule.instance();
            module.setTopHudY(Integer.parseInt(yBox.getValue().trim()));
            module.setTopHudScale(Float.parseFloat(scaleBox.getValue().trim()));
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("Top Y 必须是整数，Scale 必须是 0.5–2.0");
        }
    }
}

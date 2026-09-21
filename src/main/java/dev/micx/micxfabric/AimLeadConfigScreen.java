package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Forge-shaped AimLead controls with live RTT diagnostics and bounded values. */
public final class AimLeadConfigScreen extends ModuleConfigScreen {
    private EditBox minDistBox;
    private EditBox maxGhostsBox;
    private EditBox extraMsBox;
    private EditBox manualPingBox;

    public AimLeadConfigScreen(Screen parent) {
        super(parent, UiText.shown("瞄准提前量", "AimLead"), UiText.shown("AimLead · 服务端移动采样", "瞄准提前量 · server movement samples"));
    }

    @Override
    protected void rebuildWidgets() {
        AimLeadConfig config = AimLeadModule.instance().config();
        minDistBox = box("minDist", Integer.toString(config.minDist), 3);
        maxGhostsBox = box("maxGhosts", Integer.toString(config.maxGhosts), 2);
        extraMsBox = box("extraMs", Integer.toString(config.extraMs), 4);
        manualPingBox = box("manualPing", Integer.toString(config.manualPing), 3);
        addRenderableWidget(minDistBox);
        addRenderableWidget(maxGhostsBox);
        addRenderableWidget(extraMsBox);
        addRenderableWidget(manualPingBox);
    }

    private EditBox box(String name, String value, int maxLength) {
        EditBox box = new EditBox(font, 0, 0, 72, 20, Component.literal(name));
        box.setMaxLength(maxLength);
        box.setValue(value);
        return box;
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        AimLeadModule module = AimLeadModule.instance();
        AimLeadConfig config = module.config();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("只用服务端 movement packet 记下来的轨迹做预测。", "只使用服务端 movement packet 轨迹，不伪造 serverPos。"), contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;

        section(graphics, UiText.shown("实时延迟", "PING (LIVE) / 延迟"), y);
        y += 20;
        graphics.text(font, UiText.shown("当前延迟", "Ping"), contentLeft(), y + 4, TEXT);
        graphics.text(font, module.effectivePing() + " ms  · " + module.pingSource()
                + "  · lead " + module.tauMs() + " ms", contentLeft() + 74, y + 4, TEXT_DIM);
        y += 26;
        graphics.text(font, UiText.shown("游戏 RTT", "Game RTT"), contentLeft(), y + 4, TEXT);
        graphics.text(font, module.gameRtt() >= 0 ? module.gameRtt() + " ms" : "waiting", contentLeft() + 74, y + 4, TEXT_DIM);
        y += 28;
        graphics.text(font, UiText.shown("自动测量延迟", "Auto Ping"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.autoPing,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("用游戏 RTT 探测", "Game RTT probe"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.gameRtt,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("仅 Zombies 生效", "Zombies Only"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.zombiesOnly,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("预测诊断", "DIAG / 预测诊断"), y);
        y += 20;
        // 转向/打转状态 + 提前量误差（τ 前预测点与现状的中位距离）
        AimLeadModule.LeadDiagnostics diag = module.leadDiagnostics();
        boolean turning = diag.turnDegPerSec() >= AimLeadRoundRules.TURN_DEG_PER_SEC;
        String turnLabel = diag.circling() ? "打转 · lead ×" + AimLeadRoundRules.TAU_SCALE_CIRCLING
                : (turning ? "转向中 · lead ×" + AimLeadRoundRules.TAU_SCALE_TURNING : "直行");
        graphics.text(font, UiText.shown("转向速度", "Turn Rate"), contentLeft(), y + 4, TEXT);
        graphics.text(font, Math.round(diag.turnDegPerSec()) + " °/s · " + turnLabel,
                contentLeft() + 74, y + 4, TEXT_DIM);
        y += 20;
        graphics.text(font, UiText.shown("提前量误差", "Lead Error"), contentLeft(), y + 4, TEXT);
        graphics.text(font, diag.leadErrSamples() > 0
                        ? String.format(java.util.Locale.ROOT, "%.2f 格 · %d 样本",
                            diag.leadErrBlocks(), diag.leadErrSamples())
                        : "waiting",
                contentLeft() + 74, y + 4, TEXT_DIM);
        y += 28;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("显示", "DISPLAY / 显示"), y);
        y += 20;
        graphics.text(font, UiText.shown("幽灵框", "Ghost Box"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.renderGhost,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("开火点", "Fire Dot"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.fireDot,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("连线", "Link Line"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.drawLink,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("服务端影子", "Server Shadow"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.serverShadow,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("数值", "VALUES / 数值"), y);
        y += 20;
        graphics.text(font, UiText.shown("最小距离", "Min Dist"), contentLeft(), y + 4, TEXT);
        minDistBox.setX(contentRight() - 72);
        minDistBox.setY(y);
        y += 28;
        graphics.text(font, UiText.shown("幽灵框上限", "Max Ghosts"), contentLeft(), y + 4, TEXT);
        maxGhostsBox.setX(contentRight() - 72);
        maxGhostsBox.setY(y);
        y += 28;
        graphics.text(font, UiText.shown("额外提前量", "Extra Lead"), contentLeft(), y + 4, TEXT);
        extraMsBox.setX(contentRight() - 72);
        extraMsBox.setY(y);
        y += 28;
        graphics.text(font, UiText.shown("手动延迟", "Manual Ping"), contentLeft(), y + 4, TEXT);
        manualPingBox.setX(contentRight() - 72);
        manualPingBox.setY(y);
        y += 34;
        y = wrapped(graphics, UiText.shown("算法沿用 Forge 版：取 12 个样本的中位数速度，滤掉 8 m/s 的异常尖峰，超过 15 m/s 就不画幽灵框。预测时间可填 50–1200 ms；提前量不到 0.3 格时也不画。", "Forge 语义：12 个样本、中位数速度、8 m/s 尖峰过滤、15 m/s 隐藏；预测时间 50–1200 ms，幽灵提前不足 0.3 格时不显示。"),
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 8;
        y = wrapped(graphics, UiText.shown("预测出来的位移会被方块碰撞夹一下。目标消失、换世界或关掉模块时会清空轨迹。", "碰撞夹取只限制预测位移；实体消失、世界切换或禁用时清空轨迹。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        y += 16;
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            AimLeadModule module = AimLeadModule.instance();
            AimLeadConfig config = module.config();
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setEnabled(!module.enabled());
                return true;
            }
            y += 38 + 20 + 26 + 28;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.autoPing = !config.autoPing;
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.gameRtt = !config.gameRtt;
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.zombiesOnly = !config.zombiesOnly;
                return true;
            }
            y += 38 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.renderGhost = !config.renderGhost;
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.fireDot = !config.fireDot;
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.drawLink = !config.drawLink;
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.serverShadow = !config.serverShadow;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        try {
            AimLeadConfig config = AimLeadModule.instance().config();
            config.minDist = Integer.parseInt(minDistBox.getValue().trim());
            config.maxGhosts = Integer.parseInt(maxGhostsBox.getValue().trim());
            config.extraMs = Integer.parseInt(extraMsBox.getValue().trim());
            config.manualPing = Integer.parseInt(manualPingBox.getValue().trim());
            config.minDist = Math.max(5, Math.min(30, config.minDist));
            config.maxGhosts = Math.max(1, Math.min(8, config.maxGhosts));
            config.extraMs = Math.max(-100, Math.min(400, config.extraMs));
            config.manualPing = Math.max(0, Math.min(600, config.manualPing));
            config.save();
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("AimLead 数值必须是有效整数");
        }
    }
}

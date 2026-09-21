package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Forge-shaped TeamSync settings without exposing token contents. */
public final class TeamSyncConfigScreen extends ModuleConfigScreen {
    private EditBox serverUrlBox;
    private EditBox tokenUrlBox;
    private EditBox updateIntervalBox;
    private EditBox rosterCheckBox;
    private EditBox hudOffsetBox;
    private EditBox hudYBox;
    private boolean listening;

    public TeamSyncConfigScreen(Screen parent) {
        super(parent, UiText.shown("队伍同步", "TeamSync"), UiText.shown("TeamSync · 安全连接与本地显示", "队伍同步 · 安全连接与本地显示"));
    }

    @Override
    protected void rebuildWidgets() {
        TeamSyncConfig config = TeamSyncModule.instance().config();
        serverUrlBox = box("serverUrl", config.serverUrl, 160);
        tokenUrlBox = box("tokenUrl", config.tokenUrl, 160);
        updateIntervalBox = box("updateIntervalMs", Integer.toString(config.updateIntervalMs), 5);
        rosterCheckBox = box("rosterCheckMs", Integer.toString(config.rosterCheckMs), 5);
        hudOffsetBox = box("hudRightOffset", Integer.toString(config.hudRightOffset), 5);
        hudYBox = box("hudY", Integer.toString(config.hudY), 5);
        addRenderableWidget(serverUrlBox);
        addRenderableWidget(tokenUrlBox);
        addRenderableWidget(updateIntervalBox);
        addRenderableWidget(rosterCheckBox);
        addRenderableWidget(hudOffsetBox);
        addRenderableWidget(hudYBox);
    }

    private EditBox box(String name, String value, int maxLength) {
        EditBox box = new EditBox(font, 0, 0, 190, 20, Component.literal(name));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        box.setBordered(true);
        return box;
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        TeamSyncModule module = TeamSyncModule.instance();
        TeamSyncConfig config = module.config();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "连接失败只影响同步，不阻塞游戏主线程。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("连接状态", "Connection"), contentLeft(), y + 4, TEXT);
        graphics.text(font, (module.connected() ? "connected" : "disconnected")
                + (module.joined() ? " · joined" : " · waiting"), contentLeft() + 100, y + 4, TEXT_DIM);
        y += 28;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("连接地址", "ENDPOINT / 连接地址"), y);
        y += 20;
        graphics.text(font, UiText.shown("WebSocket 地址", "WebSocket"), contentLeft(), y + 4, TEXT);
        place(serverUrlBox, contentRight() - 190, y);
        y += 28;
        graphics.text(font, UiText.shown("Token 地址", "Token HTTPS"), contentLeft(), y + 4, TEXT);
        place(tokenUrlBox, contentRight() - 190, y);
        y += 34;
        y = wrapped(graphics, UiText.shown("地址只接受 wss / https，且不能带 userinfo、query 或 fragment。token 内容不会被显示或保存。", "仅接受 wss / https、无 userinfo、query 或 fragment；不会显示或保存 token 内容。"),
                contentLeft(), y, TEXT_DIM, contentWidth());

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("显示", "DISPLAY / 显示"), y);
        y += 20;
        graphics.text(font, UiText.shown("HUD 叠层", "HUD overlay"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.renderOverlay,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("世界标记", "World markers"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.renderWorld,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("本地瞄准兜底", "Local target"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.localAimFallback,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("显示延迟", "Show ping"), contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, config.showPing,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("延迟测试键", "Ping Button"), contentLeft(), y + 4, TEXT);
        drawButton(graphics, pingButtonLabel(config.pingButton), contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 34;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("周期", "TIMING / 周期"), y);
        y += 20;
        graphics.text(font, UiText.shown("状态上报间隔", "State interval"), contentLeft(), y + 4, TEXT);
        place(updateIntervalBox, contentRight() - 72, y);
        y += 28;
        graphics.text(font, UiText.shown("队伍检测间隔", "Roster check"), contentLeft(), y + 4, TEXT);
        place(rosterCheckBox, contentRight() - 72, y);
        y += 34;
        y = wrapped(graphics, UiText.shown("状态上报间隔 50–5000 毫秒，队伍检测间隔 500–10000 毫秒。连接和取 token 都在独立线程里跑，不会卡住游戏。", "状态上报 50–5000 ms；队伍检测 500–10000 ms。连接和 token 请求在独立线程执行。"),
                contentLeft(), y, TEXT_DIM, contentWidth());

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("位置", "LAYOUT / 位置"), y);
        y += 20;
        graphics.text(font, UiText.shown("右侧偏移", "Right offset"), contentLeft(), y + 4, TEXT);
        place(hudOffsetBox, contentRight() - 72, y);
        y += 28;
        graphics.text(font, UiText.shown("HUD 垂直位置", "HUD Y"), contentLeft(), y + 4, TEXT);
        place(hudYBox, contentRight() - 72, y);
        y += 34;
        section(graphics, UiText.shown("快捷键", "KEYBIND / 快捷键"), y);
        y += 20;
        graphics.text(font, UiText.shown("开关快捷键", "Toggle"), contentLeft(), y + 4, TEXT);
        String label = listening ? "按任意键或鼠标键 · ESC 取消" : new InputBinding(config.toggleKeyCode).label();
        drawButton(graphics, label, contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 34;
        y = wrapped(graphics, UiText.shown("主键第一次按是开模块，再按一次是开关 HUD。鼠标键沿用 Forge 的编码方式。", "主键首次按下启用模块；再次按下切换 HUD。Ping 鼠标键沿用 Forge 编码 -100 + button。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    private void place(EditBox box, int x, int y) {
        box.setX(x);
        box.setY(y);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening && event.button() != 0) {
            TeamSyncModule.instance().config().toggleKeyCode = -100 + event.button();
            listening = false;
            return true;
        }
        if (event.button() == 0) {
            TeamSyncModule module = TeamSyncModule.instance();
            TeamSyncConfig config = module.config();
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                module.setEnabled(!module.enabled());
                return true;
            }
            y += 38 + 28 + 14 + 20 + 28 + 34 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.renderOverlay = !config.renderOverlay;
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.renderWorld = !config.renderWorld;
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.localAimFallback = !config.localAimFallback;
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                config.showPing = !config.showPing;
                return true;
            }
            y += 38;
            if (isInside(event.x(), event.y(), contentRight() - 156, y, 156, 18)) {
                config.pingButton = config.pingButton >= 2 ? -1 : config.pingButton + 1;
                return true;
            }
            y += 34 + 14 + 20 + 28 + 34 + 14 + 20 + 28 + 34 + 20;
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
            if (event.isEscape()) listening = false;
            else {
                TeamSyncModule.instance().config().toggleKeyCode = event.key();
                listening = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void saveAndClose() {
        try {
            TeamSyncConfig config = TeamSyncModule.instance().config();
            config.serverUrl = TeamSyncConfig.safeUrl(serverUrlBox.getValue(), TeamSyncConfig.DEFAULT_SERVER_URL, "wss");
            config.tokenUrl = TeamSyncConfig.safeUrl(tokenUrlBox.getValue(), TeamSyncConfig.DEFAULT_TOKEN_URL, "https");
            config.updateIntervalMs = Integer.parseInt(updateIntervalBox.getValue().trim());
            config.rosterCheckMs = Integer.parseInt(rosterCheckBox.getValue().trim());
            config.hudRightOffset = Integer.parseInt(hudOffsetBox.getValue().trim());
            config.hudY = Integer.parseInt(hudYBox.getValue().trim());
            config.updateIntervalMs = Math.max(50, Math.min(5_000, config.updateIntervalMs));
            config.rosterCheckMs = Math.max(500, Math.min(10_000, config.rosterCheckMs));
            config.hudRightOffset = Math.max(20, Math.min(9_999, config.hudRightOffset));
            config.hudY = Math.max(0, Math.min(9_999, config.hudY));
            TeamSyncModule.instance().saveConfig();
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("TeamSync 数值必须是有效整数");
        }
    }

    /** Forge btnCn 同款：-1=Off 0=Left 1=Right 2=Middle。 */
    private static String pingButtonLabel(int button) {
        return switch (button) {
            case 0 -> "Left";
            case 1 -> "Right";
            case 2 -> "Middle";
            default -> "Off";
        };
    }
}

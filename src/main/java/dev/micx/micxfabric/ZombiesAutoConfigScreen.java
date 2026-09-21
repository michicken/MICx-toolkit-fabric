package dev.micx.micxfabric;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Forge ZombiesAutoPanelScreen equivalent for Fabric 26.2. */
public final class ZombiesAutoConfigScreen extends ZombiesSubConfigScreen {
    public ZombiesAutoConfigScreen(Screen parent) {
        super(parent, UiText.shown("记分板 / 消息", "ZombiesAssist · Auto"), UiText.shown("ZombiesAssist · Auto & Chat", "记分板 / 消息 · 自动行为"));
    }

    @Override
    protected List<ToggleRow> toggleRows() {
        ZombiesConfig c = ZombiesAssistModule.instance().config();
        return List.of(
                toggle("/pc Round", "每回合只发送一次 [R#] 怪物构成与战术信息。", () -> c.pcRoundInfo, v -> c.pcRoundInfo = v),
                toggle("Post-game Stats", "Game Over 时只输出一次本地赛后统计。", () -> c.postGameStats, v -> c.postGameStats = v),
                toggle("Auto Notices", "状态从关闭到开启时发送一次本地提示。", () -> c.autoNotices, v -> c.autoNotices = v),
                toggle("No Rotate", "服务器位置包正常应用后恢复本地视角，不修改或拦截网络包。", () -> c.noRotate, v -> c.noRotate = v),
                toggle("Economy Hints", "自动显示经济落后和 ECO 提醒。", () -> c.ecoHints, v -> c.ecoHints = v),
                toggle("Power-up Forecast", "自动维护掉落组锁定和激活倒计时。", () -> c.showPowerups, v -> c.showPowerups = v)
        );
    }
}

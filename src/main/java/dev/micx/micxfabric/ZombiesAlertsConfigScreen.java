package dev.micx.micxfabric;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Forge ZombiesAlertsPanelScreen equivalent for Fabric 26.2. */
public final class ZombiesAlertsConfigScreen extends ZombiesSubConfigScreen {
    public ZombiesAlertsConfigScreen(Screen parent) {
        super(parent, "Zombies · Alerts", "提示与警报 · TOO / FR / LS");
    }

    @Override
    protected List<ToggleRow> toggleRows() {
        ZombiesConfig c = ZombiesAssistModule.instance().config();
        return List.of(
                toggle("TOO Alert", "识别 The Old One 并显示实体预警。", () -> c.tooAlert, v -> c.tooAlert = v),
                toggle("TOO Rush", "残怪 20 只以内监测 TOO 快速逼近。", () -> c.tooRushAlert, v -> c.tooRushAlert = v),
                toggle("Threat Counts", "在准心附近显示 TOO / Giant / Clown 数量。", () -> c.specialThreatHud, v -> c.specialThreatHud = v),
                toggle("TOO Strict Green", "TOO 判定要求绿色皮革胸甲，避免误报。", () -> c.tooStrictGreen, v -> c.tooStrictGreen = v),
                toggle("Block Alert", "史莱姆 + 巨人回合显示 BLOCK NOW。", () -> c.blockAlert, v -> c.blockAlert = v),
                toggle("Eco Hints", "显示逐回合 ECO 战术建议。", () -> c.ecoHints, v -> c.ecoHints = v),
                toggle("LS Assist", "显示 LS 回合的倒地和队伍状态。", () -> c.lsAssist, v -> c.lsAssist = v),
                toggle("FR Coach", "显示逐玩家 Fast Revive 冷却和救援提示。", () -> c.frCoach, v -> c.frCoach = v)
        );
    }

    @Override
    protected List<NumericSpec> numericSpecs() {
        return List.of(
                integer("frHudXOffset", "FR X offset", "Fast Revive 区域水平偏移。", -5_000, 5_000,
                        z -> z.frHudXOffset, (z, v) -> z.frHudXOffset = v.intValue()),
                integer("frHudYOffset", "FR Y offset", "Fast Revive 区域垂直偏移。", -5_000, 5_000,
                        z -> z.frHudYOffset, (z, v) -> z.frHudYOffset = v.intValue()),
                decimal("frHudScale", "FR scale", "Fast Revive 区域缩放。", 0.5f, 2.0f,
                        z -> z.frHudScale, (z, v) -> z.frHudScale = v.floatValue()),
                integer("lsHudXOffset", "LS X offset", "LS 教练区域水平偏移。", -5_000, 5_000,
                        z -> z.lsHudXOffset, (z, v) -> z.lsHudXOffset = v.intValue()),
                integer("lsHudYOffset", "LS Y offset", "LS 教练区域垂直偏移。", -5_000, 5_000,
                        z -> z.lsHudYOffset, (z, v) -> z.lsHudYOffset = v.intValue()),
                decimal("lsHudScale", "LS scale", "LS 教练区域缩放。", 0.5f, 2.0f,
                        z -> z.lsHudScale, (z, v) -> z.lsHudScale = v.floatValue()),
                integer("threatHudXOffset", "Threat X offset", "特殊威胁区域水平偏移。", -5_000, 5_000,
                        z -> z.threatHudXOffset, (z, v) -> z.threatHudXOffset = v.intValue()),
                integer("threatHudYOffset", "Threat Y offset", "特殊威胁区域垂直偏移。", -5_000, 5_000,
                        z -> z.threatHudYOffset, (z, v) -> z.threatHudYOffset = v.intValue()),
                decimal("threatHudScale", "Threat scale", "特殊威胁区域缩放。", 0.5f, 2.0f,
                        z -> z.threatHudScale, (z, v) -> z.threatHudScale = v.floatValue()),
                integer("tooRushXOffset", "TOO rush X", "TOO rush 提示水平偏移。", -5_000, 5_000,
                        z -> z.tooRushXOffset, (z, v) -> z.tooRushXOffset = v.intValue()),
                integer("tooRushYOffset", "TOO rush Y", "TOO rush 提示垂直偏移。", -5_000, 5_000,
                        z -> z.tooRushYOffset, (z, v) -> z.tooRushYOffset = v.intValue()),
                decimal("tooRushScale", "TOO rush scale", "TOO rush 提示缩放。", 0.5f, 2.0f,
                        z -> z.tooRushScale, (z, v) -> z.tooRushScale = v.floatValue()),
                integer("blockAlertXOffset", "Block X offset", "BLOCK NOW 提示水平偏移。", -5_000, 5_000,
                        z -> z.blockAlertXOffset, (z, v) -> z.blockAlertXOffset = v.intValue()),
                integer("blockAlertYOffset", "Block Y offset", "BLOCK NOW 提示垂直偏移。", -5_000, 5_000,
                        z -> z.blockAlertYOffset, (z, v) -> z.blockAlertYOffset = v.intValue()),
                decimal("blockAlertScale", "Block scale", "BLOCK NOW 提示缩放。", 0.5f, 2.0f,
                        z -> z.blockAlertScale, (z, v) -> z.blockAlertScale = v.floatValue())
        );
    }
}

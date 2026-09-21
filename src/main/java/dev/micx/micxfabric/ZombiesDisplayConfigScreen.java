package dev.micx.micxfabric;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Forge ZombiesDisplayPanelScreen equivalent for Fabric 26.2. */
public final class ZombiesDisplayConfigScreen extends ZombiesSubConfigScreen {
    public ZombiesDisplayConfigScreen(Screen parent) {
        super(parent, UiText.shown("显示开关", "ZombiesAssist · Display"), UiText.shown("ZombiesAssist · Display", "显示区域 · HUD 与 Power-up"));
    }

    @Override
    protected List<ToggleRow> toggleRows() {
        ZombiesConfig c = ZombiesAssistModule.instance().config();
        return List.of(
                toggle(UiText.shown("HUD 叠层", "HUD Overlay"), "显示顶部波次、剩余僵尸和战术信息。", () -> c.overlayEnabled, v -> c.overlayEnabled = v),
                toggle("Mobs Left", "显示当前回合怪物剩余数。", () -> c.showMobs, v -> c.showMobs = v),
                toggle("PU Forecast", "显示已锁定的 Max Ammo / Insta Kill / Shopping Spree 预测。", () -> c.showPowerups, v -> c.showPowerups = v),
                toggle("PU Beam", "显示掉落 Power-up 的世界光柱；不影响 tracker 计时。", () -> c.puBeam, v -> c.puBeam = v),
                toggle("Hits / Crit", "显示最近射击的命中率与暴击。", () -> c.showStats, v -> c.showStats = v),
                toggle("Economy", "显示当前金币与队友金币表。", () -> c.showEconomy, v -> c.showEconomy = v),
                toggle("Wave Tempo", "显示当前清怪 ETA 与下一波叠波余量。", () -> c.waveTempo, v -> c.waveTempo = v),
                toggle("Slime Growth", "显示史莱姆成长档和开打时机。", () -> c.slimeGrowth, v -> c.slimeGrowth = v),
                toggle("Wave Table", "回合波次时间表 HUD（含 Speedrun 分段）。", () -> c.waveTableHud, v -> c.waveTableHud = v),
                toggle("Original Scoreboard", "保留 Hypixel 原版 scoreboard；关闭时只隐藏可恢复的原生 HUD 元素。", () -> c.originalScoreboard, v -> c.originalScoreboard = v)
        );
    }

    @Override
    protected List<NumericSpec> numericSpecs() {
        ZombiesConfig c = ZombiesAssistModule.instance().config();
        return List.of(
                integer("tacticalHudRight", "Tactical right", "战术区距右边缘。", 0, 9_999,
                        z -> z.tacticalHudRight, (z, v) -> z.tacticalHudRight = v.intValue()),
                integer("tacticalHudY", "Tactical Y", "战术区顶部 Y。", 0, 9_999,
                        z -> z.tacticalHudY, (z, v) -> z.tacticalHudY = v.intValue()),
                decimal("tacticalHudScale", "Tactical scale", "战术区缩放。", 0.5f, 2.0f,
                        z -> z.tacticalHudScale, (z, v) -> z.tacticalHudScale = v.floatValue()),
                integer("ecoHudRight", "Economy right", "经济区距右边缘。", 0, 9_999,
                        z -> z.ecoHudRight, (z, v) -> z.ecoHudRight = v.intValue()),
                integer("ecoHudCenterYOffset", "Economy Y offset", "经济区中心 Y 偏移。", -5_000, 5_000,
                        z -> z.ecoHudCenterYOffset, (z, v) -> z.ecoHudCenterYOffset = v.intValue()),
                decimal("ecoHudScale", "Economy scale", "经济区缩放。", 0.5f, 2.0f,
                        z -> z.ecoHudScale, (z, v) -> z.ecoHudScale = v.floatValue()),
                integer("topHudXOffset", "Top X offset", "顶部 HUD 水平偏移。", -5_000, 5_000,
                        z -> z.topHudXOffset, (z, v) -> z.topHudXOffset = v.intValue()),
                integer("topHudY", UiText.shown("顶部 Y 坐标", "Top Y"), "顶部 HUD 垂直位置。", -5_000, 5_000,
                        z -> z.topHudY, (z, v) -> z.topHudY = v.intValue()),
                decimal("topHudScale", "Top scale", "顶部 HUD 缩放。", 0.5f, 2.0f,
                        z -> z.topHudScale, (z, v) -> z.topHudScale = v.floatValue()),
                integer("puHudRight", "Power-up right", "Power-up 面板距右边缘。", 0, 9_999,
                        z -> z.puHudRight, (z, v) -> z.puHudRight = v.intValue()),
                integer("puHudBottom", "Power-up bottom", "Power-up 面板距底部。", 0, 9_999,
                        z -> z.puHudBottom, (z, v) -> z.puHudBottom = v.intValue()),
                decimal("puHudScale", "Power-up scale", "Power-up 面板缩放。", 0.5f, 2.0f,
                        z -> z.puHudScale, (z, v) -> z.puHudScale = v.floatValue()),
                integer("ecoClockRight", "ECO clock right", "ECO 时钟距右边缘。", 0, 9_999,
                        z -> z.ecoClockRight, (z, v) -> z.ecoClockRight = v.intValue()),
                integer("ecoClockBottom", "ECO clock bottom", "ECO 时钟距底部。", 0, 9_999,
                        z -> z.ecoClockBottom, (z, v) -> z.ecoClockBottom = v.intValue()),
                decimal("ecoClockScale", "ECO clock scale", "ECO 时钟缩放。", 0.5f, 2.0f,
                        z -> z.ecoClockScale, (z, v) -> z.ecoClockScale = v.floatValue())
        );
    }
}

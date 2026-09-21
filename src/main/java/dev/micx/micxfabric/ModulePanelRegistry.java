package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static dev.micx.micxfabric.UiText.revised;
import static dev.micx.micxfabric.UiText.unchanged;

/**
 * Panel metadata; runtime registration is maintained separately in ModuleRuntime.
 *
 * <p>模块的英文短名（{@code displayName}）固定是代码里的类名风格，作面板里的小字副标；
 * 中文名作主标题，说明作介绍。中文名与说明都走 {@link UiText} 的双语义机制：改写后的
 * 文案放在前，改写前的原文放在后，面板顶部按钮切换。名字没动过的用 {@code unchanged}
 * 包一下，两版显示同一句。
 */
public final class ModulePanelRegistry {
    public static final String GROUP_CORE = "core";
    public static final String GROUP_VISION = "vision";
    public static final String GROUP_ACTION = "action";
    public static final String GROUP_INFO = "info";
    public static final String GROUP_SQUAD = "squad";
    public static final String GROUP_CHAT = "chat";
    public static final String GROUP_MISC = "misc";
    public static final String GROUP_DEBUG = "debug";
    // 兼容旧引用
    public static final String GROUP_COMBAT = GROUP_ACTION;
    public static final List<String> GROUP_ORDER = List.of(
            GROUP_CORE, GROUP_VISION, GROUP_ACTION, GROUP_INFO, GROUP_SQUAD, GROUP_CHAT, GROUP_MISC, GROUP_DEBUG);

    private static final Map<String, GroupMetadata> GROUPS = new LinkedHashMap<>();
    private static final Map<String, ModulePanelDescriptor> DESCRIPTORS = new LinkedHashMap<>();
    private static final Map<String, List<SubmoduleDescriptor>> SUBMODULES = new LinkedHashMap<>();
    private static int nextOrder;
    /** Forge ModuleMeta order 映射：用于与 1.8.9 左栏排序一致，缺口回退 nextOrder。 */
    private static final Map<String, Integer> META_ORDER = new LinkedHashMap<>();
    private static final Map<String, String> META_GROUP = new LinkedHashMap<>();

    private static void meta(String id, String group, int order) {
        META_GROUP.put(id, group);
        META_ORDER.put(id, order);
    }

    static {
        // 复刻 src/main/java/dev/micx/micxtoolkit/gui/ModuleMeta.java 的分组与 order
        meta("zombies_assist", GROUP_CORE, 0);
        meta("lr_indicator", GROUP_CORE, 1);
        meta("anti_axe", GROUP_CORE, 2);
        meta("sword_block", GROUP_CORE, 10);
        meta("round_timer", GROUP_CORE, 11);
        meta("eco_rate", GROUP_CORE, 12);
        meta("window_spawn_counter", GROUP_CORE, 13);
        meta("remote_shop", GROUP_CORE, 14);
        meta("esp", GROUP_VISION, 0);
        meta("spawn_marker", GROUP_VISION, 1);
        meta("golem_marker", GROUP_VISION, 2);
        meta("slime_forecast", GROUP_VISION, 3);
        meta("chams", GROUP_VISION, 4);
        meta("zombie_fade", GROUP_VISION, 5);
        meta("player_outline_esp", GROUP_VISION, 6);
        meta("last_mobs", GROUP_VISION, 7);
        meta("fullbright", GROUP_VISION, 8);
        meta("aimbot", GROUP_ACTION, 0);
        meta("aimbot_hud", GROUP_ACTION, 1);
        meta("aim_lead", GROUP_ACTION, 2);
        meta("magnet", GROUP_ACTION, 2);
        meta("right_clicker", GROUP_ACTION, 2);
        meta("skill_cast", GROUP_ACTION, 3);
        meta("keyboard_clicker", GROUP_ACTION, 4);
        meta("noreload", GROUP_ACTION, 4);
        meta("revive_aura", GROUP_ACTION, 4);
        meta("dps_counter", GROUP_INFO, 0);
        meta("toro_health", GROUP_INFO, 1);
        meta("teammate_hp", GROUP_INFO, 2);
        meta("team_sync", GROUP_SQUAD, 0);
        meta("chat_cleaner", GROUP_CHAT, 0);
        meta("chat_translate", GROUP_CHAT, 1);
        meta("chat_message_translate", GROUP_CHAT, 2);
        meta("chat_copy", GROUP_CHAT, 3);
        meta("auto_text", GROUP_CHAT, 4);
        meta("rank_up_tool", GROUP_CHAT, 5);
        meta("welcome", GROUP_CHAT, 6);
        meta("zoom_scope", GROUP_MISC, 0);
        meta("view_hold", GROUP_MISC, 1);
        meta("toggle_sprint", GROUP_MISC, 2);
        meta("player_visibility", GROUP_MISC, 3);
        meta("auto_hide_visuals", GROUP_MISC, 4);
        meta("asr", GROUP_MISC, 6);
        meta("anti_reshift", GROUP_MISC, 7);
        meta("legacy_sneak_visuals", GROUP_MISC, 5);

        GROUPS.put(GROUP_CORE, new GroupMetadata("CORE", revised("僵尸模式核心", "Zombies 核心")));
        GROUPS.put(GROUP_VISION, new GroupMetadata("VISION", revised("视觉 · 透视与标记", "战斗 · 视觉")));
        GROUPS.put(GROUP_ACTION, new GroupMetadata("ACTION", revised("操作 · 瞄准与点击", "战斗 · 操作")));
        GROUPS.put(GROUP_INFO, new GroupMetadata("HUD & INFO", revised("信息面板 · HUD", "信息面板")));
        GROUPS.put(GROUP_SQUAD, new GroupMetadata("SQUAD", unchanged("队伍协同")));
        GROUPS.put(GROUP_CHAT, new GroupMetadata("CHAT", unchanged("聊天")));
        GROUPS.put(GROUP_MISC, new GroupMetadata("MISC", revised("杂项 · 视角与输入", "视觉 · 输入")));
        GROUPS.put(GROUP_DEBUG, new GroupMetadata("DEBUG", revised("调试 · 排错", "调试")));

        real("sword_block", "SwordBlock", revised("剑格挡动画", "剑格挡"), GROUP_CORE,
                revised("客户端本地播放的剑格挡动画，只是看起来在格挡；它不会让服务端少算你受到的伤害。",
                        "客户端视觉格挡动画，不提供服务端伤害减免。"),
                SwordBlockModule.instance(), SwordBlockConfigScreen::new);
        real("round_timer", "RoundTimer", unchanged("回合计时"), GROUP_CORE,
                revised("显示当前回合已经打了多久（R{回合号} mm:ss）。这段文字和 Wave Table 放在同一区块，共用同一个位置偏移。",
                        "当前回合用时 R{n} mm:ss，与 Wave Table 同区块同偏移。"),
                RoundTimerModule.instance(), null);
        real("eco_rate", "EcoRate", revised("经济增速", "经济速率"), GROUP_CORE,
                revised("在右侧经济表上定期把金币数字换成「每 2 分钟净增多少」，绿色闪一下再切回原样。统计窗口是最近 2 分钟，每 10 秒刷新一次；显示时长与间隔可在面板里调。",
                        "右侧经济表周期性把金币切为每2分钟纯增长速率（绿字闪烁），2分钟窗口每10秒刷新。"),
                EcoRateModule.instance(), EcoRatePanelScreen::new);
        real("lr_indicator", "LR Indicator", revised("LR 释放清单", "LR 指示"), GROUP_CORE,
                revised("在 AA 的物品栏上方列一张 LR 释放状态清单：绿色表示 18 秒内已经放过，红色表示还没放。清单位置可以用 /micx lr 2 / 3 / 4 轮换，也可以在面板里改偏移。",
                        "AA 物品栏上方的 LR 释放清单：绿=已放 18s 内，红=未放，轮换位置 /micx lr 2/3/4。"),
                LrIndicatorModule.instance(), parent -> new SimpleModuleScreen(parent,
                        LrIndicatorModule.instance(), UiText.shown("LR 释放清单", "LR Indicator"), UiText.shown("LR Indicator · 蜂鸣与偏移", "LR 释放清单 · 蜂鸣与偏移"),
                        List.of(
                                SimpleModuleScreen.Row.toggle(UiText.shown("提示音", "Beep 蜂鸣"),
                                        LrIndicatorModule.instance()::lrBeepEnabled,
                                        v -> LrIndicatorModule.instance().lrBeepEnabled = v,
                                        "LR 可以释放的时候播放一声提示音。"),
                                SimpleModuleScreen.Row.integer(UiText.shown("清单水平偏移", "Offset X"),
                                        () -> LrIndicatorModule.instance().lrHudDx,
                                        v -> LrIndicatorModule.instance().lrHudDx = v, -300, 300,
                                        "清单在屏幕上左右移动多少像素（拖动 HUD Layout 里的 Wave Table 不会影响这里）。"),
                                SimpleModuleScreen.Row.integer(UiText.shown("清单垂直偏移", "Offset Y"),
                                        () -> LrIndicatorModule.instance().lrHudDy,
                                        v -> LrIndicatorModule.instance().lrHudDy = v, -300, 300,
                                        "清单在屏幕上上下移动多少像素。"))));
        real("auto_hide_visuals", "AutoHide Visuals", revised("结算自动隐藏", "自动隐藏"), GROUP_DEBUG,
                revised("对局结算后的 1 分钟内，自动把 ESP、Chams、玩家轮廓和 AimLead 暂时关掉；进入 R1 或离开地图时自动恢复。",
                        "对局结算 1 分钟藏 ESP/Chams/Outline/AimLead，R1/离图恢复。"),
                AutoHideVisualsModule.instance(), null);
        real("packet_log", "PacketLog", unchanged("封包日志"), GROUP_DEBUG,
                revised("排错用：记录你自己这条连接收发的封包。出站有交互、点格子、聊天、自定义通道，"
                                + "入站有开界面、聊天、标题、拉回等，写到 config/MICxToolkit/logs/ 下的日志文件；"
                                + "用快捷键可以插一条标记线，方便查「买弹到底走哪条通道」。",
                        "记录玩家自己这条连接的收发封包（出站交互/点格子/聊天/自定义通道，入站开界面/聊天/标题/拉回…）"
                                + "，写 config/MICxToolkit/logs/ 下的日志文件；快捷键插标记线，用来查买弹到底走哪条通道"),
                PacketLogModule.instance(), PacketLogConfigScreen::new);
        real("zombie_fade", "ZombieFade", revised("近身怪物淡化", "僵尸淡化"), GROUP_MISC,
                revised("玩家周围的敌对生物在近距离内淡化成半透明，避免贴脸时挡住视野。",
                        "近距离敌对生物半透明淡化。"),
                ZombieFadeModule.instance(), parent -> new SimpleModuleScreen(parent,
                        ZombieFadeModule.instance(), UiText.shown("近身怪物淡化", "ZombieFade"), UiText.shown("ZombieFade · 近身淡化", "近距离怪物淡化"),
                        List.of(
                                SimpleModuleScreen.Row.decimal(UiText.shown("淡化半径", "Radius 半径"),
                                        ZombieFadeModule.instance()::getRadius,
                                        ZombieFadeModule.instance()::setRadius, 1, 10,
                                        "离玩家多近（格）的敌对生物开始淡化。"),
                                SimpleModuleScreen.Row.decimal("Alpha 不透明度",
                                        ZombieFadeModule.instance()::getAlpha,
                                        ZombieFadeModule.instance()::setAlpha, 0.05, 1,
                                        "淡化后的不透明度，越小越透明（0.05~1）。"))));
        real("particle_free", "NoParticles", unchanged("粒子屏蔽"), GROUP_MISC,
                revised("屏蔽所有粒子效果，爆炸、方块破坏等产生的粒子一并关掉，语义与 1.8.9 的「完全关闭」一致。",
                        "屏蔽所有粒子效果（爆炸/破坏等一切），匹配 1.8.9 的完全关闭语义。"),
                ParticleFreeModule.instance(), null);
        real("revive_aura", "ReviveAura", unchanged("自动救人"), GROUP_CORE,
                revised("队友倒地睡在附近时，自动替他发一个救援交互包。每人每次倒地只发一包；"
                                + "冷却按目标分开算——刚点过 A 不影响马上点 B，Interval 只限制对同一个人的连点，每 tick 最多发一包。",
                        "队友倒地睡在附近时自动发救援交互包：每人每次倒地一包；冷却按目标分开——"
                                + "刚点过 A 不影响立刻点 B，interval 只挡对同一只的连点，每 tick 最多一包。"),
                ReviveAuraModule.instance(), parent -> new SimpleModuleScreen(parent,
                        ReviveAuraModule.instance(), UiText.shown("自动救人", "ReviveAura"), UiText.shown("ReviveAura · 自动救援发包", "自动救援发包"),
                        List.of(
                                SimpleModuleScreen.Row.decimal("Range 范围",
                                        ReviveAuraModule.instance()::getRange,
                                        ReviveAuraModule.instance()::setRange, 1, 10,
                                        "和倒地队友之间多远之内才会发救援包（格）。"),
                                SimpleModuleScreen.Row.decimal("Interval 间隔(ms)",
                                        ReviveAuraModule.instance()::getIntervalMs,
                                        ReviveAuraModule.instance()::setIntervalMs, 50, 1000,
                                        "对同一个目标两次发包之间的最小间隔（毫秒）。冷却按目标分开，点过 A 不影响马上点 B。"))));
        real("last_mobs", "LastMobs", revised("剩余怪连线", "残怪连线"), GROUP_INFO,
                revised("一个回合快清完时，从准心向每一只还没死的怪拉一条黄色指示线，帮你找剩下的怪。剩余数量按计分板的权威计数。",
                        "回合剩余怪 ≤N 时，准心向每只残怪拉黄色指示线（计分板权威计数）。"),
                LastMobsModule.instance(), parent -> new SimpleModuleScreen(parent,
                        LastMobsModule.instance(), UiText.shown("剩余怪连线", "LastMobs"), UiText.shown("LastMobs · 剩余怪牵线", "剩余怪牵线"),
                        List.of(
                                SimpleModuleScreen.Row.integer(UiText.shown("触发阈值", "Max Count 阈值"),
                                        LastMobsModule.instance()::getMaxCount,
                                        LastMobsModule.instance()::setMaxCount, 1, 10,
                                        "剩余怪物数小于等于这个值时开始画线。"),
                                SimpleModuleScreen.Row.integer(UiText.shown("线条不透明度 %", "Alpha %"),
                                        LastMobsModule.instance()::getLineAlphaPct,
                                        LastMobsModule.instance()::setLineAlphaPct, 20, 100,
                                        "指示线的不透明度百分比。"))));
        real("spawn_marker", "SpawnMarker", unchanged("刷怪点标记"), GROUP_INFO,
                revised("在 AA 已知的刷怪点上画固定的灰色光柱：11 个地面刷怪点加 4 个 UFO 放怪口。位置是预设的，不随对局变化。",
                        "AA 已知刷怪点固定灰色光柱：11 地面点 + 4 UFO 放怪口（纯预设）。"),
                SpawnMarkerModule.instance(), parent -> new SimpleModuleScreen(parent,
                        SpawnMarkerModule.instance(), UiText.shown("刷怪点标记", "SpawnMarker"), UiText.shown("SpawnMarker · 刷怪点光柱", "刷怪点光柱"),
                        List.of(
                                SimpleModuleScreen.Row.decimal(UiText.shown("不透明度", "Alpha"),
                                        SpawnMarkerModule.instance()::getAlpha,
                                        v -> SpawnMarkerModule.instance().setAlpha((float) v), 0.05, 1,
                                        "所有标记的整体透明度。"))));
        real("slime_forecast", "SlimeForecast", revised("史莱姆波预告", "史莱姆预告"), GROUP_INFO,
                revised("在会刷史莱姆或岩浆怪的波次到来之前，把 12 个固定点标成绿色 X（墨绿→亮绿→刷出后隐藏）。开启 Force 模式则一直显示。",
                        "刷史莱姆/岩浆波次前在 12 个固定点显示绿 X（墨绿→亮绿→隐藏），Force 模式常显。"),
                SlimeForecastModule.instance(), parent -> new SimpleModuleScreen(parent,
                        SlimeForecastModule.instance(), UiText.shown("史莱姆波预告", "SlimeForecast"), UiText.shown("SlimeForecast · 史莱姆波次预告", "史莱姆波次预告"),
                        List.of(
                                SimpleModuleScreen.Row.toggle("Force 常显",
                                        SlimeForecastModule.instance()::isForceMode,
                                        SlimeForecastModule.instance()::setForceMode,
                                        "无视当前阶段状态，12 个预测点一直显示。"),
                                SimpleModuleScreen.Row.decimal(UiText.shown("不透明度", "Alpha"),
                                        SlimeForecastModule.instance()::getAlpha,
                                        v -> SlimeForecastModule.instance().setAlpha((float) v), 0.05, 1,
                                        "X 标记的透明度。"))));
        real("golem_marker", "GolemMarker", unchanged("铁傀儡标记"), GROUP_INFO,
                revised("在铁傀儡的 5 个固定出生点贴地画灰色 X，穿墙可见。",
                        "铁傀儡 5 个固定出生点贴地灰 X，穿墙可见。"),
                GolemMarkerModule.instance(), parent -> new SimpleModuleScreen(parent,
                        GolemMarkerModule.instance(), UiText.shown("铁傀儡标记", "GolemMarker"), UiText.shown("GolemMarker · 铁傀儡出生点 X", "铁傀儡出生点 X"),
                        List.of(
                                SimpleModuleScreen.Row.decimal(UiText.shown("不透明度", "Alpha"),
                                        GolemMarkerModule.instance()::getAlpha,
                                        v -> GolemMarkerModule.instance().setAlpha((float) v), 0.05, 1,
                                        "X 标记的透明度。"))));
        real("zoom_scope", "ZoomScope", unchanged("放大镜"), GROUP_MISC,
                revised("按住放大镜键，屏幕中央按 16:9 放大 2~8 倍；滚轮调倍率，松开按键恢复。鼠标灵敏度会按倍率同步缩放。",
                        "按住放大镜键在屏幕中央 16:9 放大 2~8x，滚轮调倍率，松开恢复，灵敏度按 k/zoom 缩放。"),
                ZoomScopeModule.instance(), parent -> new ZoomScopePanelScreen(parent, ZoomScopeModule.instance()));
        real("view_hold", "ViewHold", revised("临时视角", "快捷视角"), GROUP_MISC,
                revised("按住绑定键临时切到背后视角或正面视角，松开自动回到第一人称。正面视角支持俯仰镜像。需要先绑定按键。",
                        "按住绑定键切到背后/正面视角，松开恢复第一人称；正面视角支持俯仰镜像（需先绑定按键）。"),
                ViewHoldModule.instance(), ViewHoldConfigScreen::new);
        real("anti_reshift", "AntiReshift", revised("救援防误松", "防松Shift"), GROUP_MISC,
                revised("救援队友的过程中不小心松开 Shift 也不会中断（Type B 方案）：不自动重按、不发任何包，默认关闭。血量过低或刚被救起来时会自动放行。",
                        "救援途中防误松 Shift（Type B）：不自动重按、不发包，默认关闭；低血/救起自动放行。"),
                AntiReshiftModule.instance(), parent -> new SimpleModuleScreen(parent,
                        AntiReshiftModule.instance(), UiText.shown("救援防误松", "AntiReshift"), UiText.shown("AntiReshift · 救援防误松 Shift", "救援防误松 Shift"),
                        List.of(
                                SimpleModuleScreen.Row.integer("Duo 起效回合",
                                        AntiReshiftModule.instance()::getDuoStartRound,
                                        AntiReshiftModule.instance()::setDuoStartRound, 1, 100,
                                        "双排从第几回合开始生效。"),
                                SimpleModuleScreen.Row.integer("3-4排 起效回合",
                                        AntiReshiftModule.instance()::getNonDuoStartRound,
                                        AntiReshiftModule.instance()::setNonDuoStartRound, 1, 100,
                                        "三排 / 四排从第几回合开始生效。"),
                                SimpleModuleScreen.Row.decimal("低血放行 HP",
                                        AntiReshiftModule.instance()::getLowHealthHp,
                                        v -> AntiReshiftModule.instance().setLowHealthHp((float) v), 1, 20,
                                        "血量低于这个值时暂停防松（双排不受这条规则限制）。"))));
        real("magnet", "Magnet", revised("视角吸附", "吸附"), GROUP_COMBAT,
                revised("按住右键时准心轻微吸向目标的爆头点；手瞄快速甩动时自动退场，只做辅助不跟你抢准心。只在 Zombies 对局里生效。",
                        "按住右键时准心轻微吸向目标爆头点，手瞄快甩自动退场；仅 Zombies 生效。"),
                MagnetModule.instance(), parent -> new SimpleModuleScreen(parent,
                        MagnetModule.instance(), UiText.shown("视角吸附", "Magnet"), UiText.shown("Magnet · 视角吸附", "视角吸附"),
                        List.of(
                                SimpleModuleScreen.Row.toggle("减速带模式",
                                        MagnetModule.instance()::isSlowMode,
                                        MagnetModule.instance()::setSlowMode,
                                        "开=对上怪物时降低灵敏度（变黏）但不主动拉；关=额外叠加牵引。"),
                                SimpleModuleScreen.Row.toggle(UiText.shown("仅命中框减速", "Hitbox Only"),
                                        MagnetModule.instance()::isHitboxOnly,
                                        MagnetModule.instance()::setHitboxOnly,
                                        "减速带的触发条件：开=射线真的打中 hitbox 才减速；关=按吸附半径的角距判定。"),
                                SimpleModuleScreen.Row.toggle(UiText.shown("仅 Zombies 生效", "Zombies Only"),
                                        MagnetModule.instance()::isZombiesOnly,
                                        MagnetModule.instance()::setZombiesOnly,
                                        "只在 Zombies 对局内生效。"),
                                SimpleModuleScreen.Row.toggle("吸 Slime",
                                        MagnetModule.instance()::isIncludeSlime,
                                        MagnetModule.instance()::setIncludeSlime,
                                        "把史莱姆 / 岩浆怪也当成吸附目标。"),
                                SimpleModuleScreen.Row.toggle("吸 Golem",
                                        MagnetModule.instance()::isIncludeGolem,
                                        MagnetModule.instance()::setIncludeGolem,
                                        "把铁傀儡也当成吸附目标。"),
                                SimpleModuleScreen.Row.toggle("吸巨人",
                                        MagnetModule.instance()::isIncludeGiant,
                                        MagnetModule.instance()::setIncludeGiant,
                                        "把巨人（Giant）也当成吸附目标。"),
                                SimpleModuleScreen.Row.decimal(UiText.shown("吸附角度范围", "Radius 度"),
                                        MagnetModule.instance()::getRadiusDeg,
                                        MagnetModule.instance()::setRadiusDeg, 2, 30,
                                        "准心偏离目标多少度以内吸附才生效。"),
                                SimpleModuleScreen.Row.decimal("Pull 强度",
                                        MagnetModule.instance()::getPullStrength,
                                        MagnetModule.instance()::setPullStrength, 0.02, 0.6,
                                        "每 tick 的收敛系数，越大吸得越快。"),
                                SimpleModuleScreen.Row.decimal("Slow 减速",
                                        MagnetModule.instance()::getSlowFactor,
                                        MagnetModule.instance()::setSlowFactor, 0.1, 0.95,
                                        "接近目标时手部的减速系数。"),
                                SimpleModuleScreen.Row.decimal(UiText.shown("爆头点停拉", "Headshot 停拉"),
                                        MagnetModule.instance()::getHeadshotStopDeg,
                                        MagnetModule.instance()::setHeadshotStopDeg, 0.5, 10.0,
                                        "进入爆头点这个角度以内就停止牵引（贴脸松、远处紧）。"))));
        real("fullbright", "Fullbright", revised("全屏亮度", "全亮"), GROUP_CORE,
                revised("强制把 gamma 拉满，洞里和夜里不再漆黑。这是 Forge 版 Fullbright 的 Fabric 等价实现，关掉后会还原原来的亮度设置。",
                        "强制 gamma 全亮，Forge Fullbright 的 Fabric 等价实现；关闭时还原。"),
                FullbrightModule.instance(), null);
        real("zombies_assist", "ZombiesAssist", unchanged("僵尸助手"), GROUP_CORE,
                revised("Zombies 比赛用的主 HUD 与播报总开关：波次与剩余怪数、Power-up、危险警报、自动行为，以及 Alien Arcadium 的实时状态。下面三个子分区分别管显示、警报、记分板与消息。",
                        "波次、僵尸剩余、Power-up、警报、自动行为和 Alien Arcadium 状态 HUD。"),
                ZombiesAssistModule.instance(), ZombiesAssistConfigScreen::new);
        real("window_spawn_counter", "WindowSpawns", revised("刷怪窗口计数", "刷怪窗口"), GROUP_CORE,
                revised("按出生点统计每个窗口刷了多少怪：P1 / P2 / P3 / P4 / P5 / ULT / ALT / CL / CR / BL / BR 共 11 个窗口。"
                                + "计数在波次内累加、跨波次清零，HUD 位置可以拖动。TOO 从某个窗口生成时该窗标红显示 T，顶部还会列出 TOO IN（持续 3 波）。",
                        "11 窗按出生点统计的刷怪量（P1/P2/P3/P4/P5/ULT/ALT/CL/CR/BL/BR），波次内累积、跨波次清，HUD 可拖动。TOO 生成时该窗显示 T 全红，顶部 TOO IN 持续 3 波。"),
                WindowSpawnCounterModule.instance(), parent -> new SimpleModuleScreen(parent,
                        WindowSpawnCounterModule.instance(), UiText.shown("刷怪窗口计数", "WindowSpawns"), UiText.shown("WindowSpawns · TOO 警报", "刷怪窗口 · TOO 警报"),
                        List.of(
                                SimpleModuleScreen.Row.toggle("TOO 窗口警报",
                                        WindowSpawnCounterModule.instance()::tooWindowAlert,
                                        WindowSpawnCounterModule.instance()::setTooWindowAlert,
                                        "TOO 出生的窗口标红 T、列出 TOO IN，并在屏幕中央提示窗口名与距离。"),
                                SimpleModuleScreen.Row.toggle(UiText.shown("/pc 队内播报", "  /pc 队内播报"),
                                        WindowSpawnCounterModule.instance()::tooWindowPc,
                                        WindowSpawnCounterModule.instance()::setTooWindowPc,
                                        "按 r58 / r59 / r101 的规则自动 /pc，以最近玩家的判定为准；每条都能单独关。"),
                                SimpleModuleScreen.Row.toggle(UiText.shown("本地叮声", "  本地叮声"),
                                        WindowSpawnCounterModule.instance()::tooWindowSound,
                                        WindowSpawnCounterModule.instance()::setTooWindowSound,
                                        "每条紫色本地提示都附一声 note.pling。"))));
        real("anti_axe", "AntiAXE", unchanged("防误领 Puncher"), GROUP_CORE,
                revised("从 Lucky Chest 抽到 The Puncher 之后的 10.5 秒内，锁住领取区的右键，避免手快把别的物品也领了；一旦领到其他物品就立刻解除锁定。",
                        "抽到 The Puncher 时锁定 Lucky Chest 领取区右键 10.5 秒，防误领；领到其他物品自动解除。"),
                AntiAxeModule.instance(), parent -> new SimpleModuleScreen(parent,
                        AntiAxeModule.instance(), UiText.shown("防误领 Puncher", "AntiAXE"), UiText.shown("AntiAXE · Puncher 领取保护", "Puncher 领取保护"),
                        List.of(
                                SimpleModuleScreen.Row.integer(UiText.shown("提示水平偏移", "HUD Offset X"),
                                        AntiAxeModule.instance()::getHudDx,
                                        AntiAxeModule.instance()::setHudDx, -300, 300,
                                        "中央提示左右移动多少像素。"),
                                SimpleModuleScreen.Row.integer(UiText.shown("提示垂直偏移", "HUD Offset Y"),
                                        AntiAxeModule.instance()::getHudDy,
                                        AntiAxeModule.instance()::setHudDy, -300, 300,
                                        "中央提示上下移动多少像素。"))));
        real("noreload", "NoReload", unchanged("免换弹"), GROUP_CORE,
                revised("按住右键时自动轮换武器槽，并给服务端发一个重置武器状态的包，跳过换弹动作。和 AutoSwitch 互斥，所以默认关闭。",
                        "按住右键轮换武器槽并发包重置武器状态跳过换弹动画；与 AutoSwitch 互斥，默认关闭。"),
                NoReloadModule.instance(), parent -> new SimpleModuleScreen(parent,
                        NoReloadModule.instance(), UiText.shown("免换弹", "NoReload"), UiText.shown("NoReload · 免换弹", "免换弹"),
                        List.of(
                                SimpleModuleScreen.Row.decimal(UiText.shown("轮换间隔 ms", "Delay ms"),
                                        NoReloadModule.instance()::getDelayMs,
                                        NoReloadModule.instance()::setDelayMs, 50, 80,
                                        "两次轮换之间隔多久；原版默认是 67ms。"),
                                SimpleModuleScreen.Row.toggle(UiText.shown("第 2 格参与轮换", "槽 2 参与轮换"),
                                        NoReloadModule.instance()::isSlot2Enabled,
                                        NoReloadModule.instance()::setSlot2,
                                        "快捷栏第 2 格也参与轮换。"),
                                SimpleModuleScreen.Row.toggle(UiText.shown("第 3 格参与轮换", "槽 3 参与轮换"),
                                        NoReloadModule.instance()::isSlot3Enabled,
                                        NoReloadModule.instance()::setSlot3,
                                        "快捷栏第 3 格也参与轮换。"),
                                SimpleModuleScreen.Row.toggle(UiText.shown("第 4 格参与轮换", "槽 4 参与轮换"),
                                        NoReloadModule.instance()::isSlot4Enabled,
                                        NoReloadModule.instance()::setSlot4,
                                        "快捷栏第 4 格也参与轮换。"),
                                SimpleModuleScreen.Row.toggle("RR 模式",
                                        NoReloadModule.instance()::isRrMode,
                                        NoReloadModule.instance()::setRrMode,
                                        "金铲子所在的槽不点击背包（近战武器不需要重置）。"))));
        real("aimbot", "Aimbot", revised("自动瞄准", "瞄准辅助"), GROUP_COMBAT,
                revised("从 1.8.9 版本迁移过来的自动瞄准：按选怪策略（忽略 / 清场 / 威胁 / 最近 / 粘性）挑目标，"
                                + "用 AimLead 的头部点瞄准，直接接管鼠标并支持 Hold-Lock 锁死目标。默认关闭。",
                        "从 1.8.9 迁移的目标筛选、优先级、AimLead 头部点、鼠标接管与 Hold-Lock；默认关闭。"),
                AimbotModule.instance(), AimbotConfigScreen::new);
        real("aimbot_hud", "Aimbot HUD", revised("Aimbot 状态组", "Aimbot HUD"), GROUP_COMBAT,
                revised("单独显示 Aimbot 的分组状态：TOO / Golem / Slime 的忽略，Clown / Giant / Baby 的优先，以及 Closest。分组快捷键也在这里处理。",
                        "独立显示 TOO/Golem/Slime 忽略、Clown/Giant/Baby 优先和 Closest 状态，并处理分组快捷键。"),
                AimbotHudModule.instance(), null);
        real("aim_lead", "AimLead", unchanged("瞄准提前量"), GROUP_COMBAT,
                revised("按服务端 movement packet 记录的轨迹预判目标接下来会走到哪里，并标出应该开火的提前点。",
                        "按服务端 movement packet 轨迹预判目标位置，标出开火提前点。"),
                AimLeadModule.instance(), AimLeadConfigScreen::new);
        real("esp", "ESP", unchanged("线框透视"), GROUP_COMBAT,
                revised("隔墙显示非玩家实体的方框轮廓，可以设作用范围、透明度和自动门控。",
                        "透过墙壁提交非玩家实体方框轮廓，带范围、透明度和自动门控。"),
                EspModule.instance(), EspConfigScreen::new);
        real("chams", "Chams", unchanged("模型透视"), GROUP_COMBAT,
                revised("用原贴图模型把被方块挡住的目标画出来，穿墙可见；和 ESP 的线框是两套独立开关。",
                        "以原贴图模型穿过墙壁显示被方块遮挡的目标；独立于 ESP 线框。"),
                ChamsModule.instance(), ChamsConfigScreen::new);
        real("player_outline_esp", "PlayerOutlineESP", unchanged("玩家轮廓"), GROUP_COMBAT,
                revised("给玩家套一圈绿色轮廓，用的是 26.2 原生 outline 阶段绘制；线条粗细由客户端原生管线决定。",
                        "使用 26.2 原生 outline phase 的玩家绿色轮廓；厚度由客户端原生管线控制。"),
                PlayerOutlineEspModule.instance(), PlayerOutlineEspConfigScreen::new);
        real("right_clicker", "RightClicker", unchanged("自动右键"), GROUP_COMBAT,
                revised("按住时真实模拟快速右键，默认 20 CPS、每 tick 最多发一发；速率可以在面板或指令里调。"
                                + "和 Aimbot 共用同一套引擎，两边同时开会自动互让，不会叠成两倍速度。",
                        "按住时真实模拟快速右键（默认 20 CPS，每 tick 一发；面板/指令可调，与 AimLead 单引擎互让）"),
                RightClickerModule.instance(), RightClickerConfigScreen::new);
        real("skill_cast", "SkillCast", revised("技能一键释放", "技能释放"), GROUP_COMBAT,
                revised("按一次走一轮：切到第 5 格 → 发一次原生右键 → 切回原来的格子。不是持续连发，只走一遍。",
                        "切槽5 + 原生右键 + 切回，单次激活"),
                SkillCastModule.instance(), SkillCastConfigScreen::new);
        real("keyboard_clicker", "KeyboardClicker", revised("自动切枪", "自动切枪 AutoSwitch"), GROUP_COMBAT,
                revised("按住右键时自动在武器槽之间轮换，用来跳过换弹和防卡弹，有多种轮换组合可选。"
                                + "同一局里如果检测到金铲子，只在它第一次出现时调整一次键位模式，之后倒地再捡起来也不会反复改。",
                        "键盘触发的自动切枪，多种组合"),
                KeyboardClickerModule.instance(), KeyboardClickerConfigScreen::new);

        real("dps_counter", "DPSCounter", unchanged("DPS 计数"), GROUP_INFO,
                revised("实时统计你自己每秒打出多少伤害（DPS）。",
                        "实时统计你的每秒伤害（DPS）"),
                DpsCounterModule.instance(), DpsCounterConfigScreen::new);
        real("toro_health", "ToroHealth", unchanged("伤害数字"), GROUP_INFO,
                revised("显示准心目标的血量和吸收盾；粒子相关的字段还留着，但尚未接入。",
                        "准心目标血量与吸收盾 HUD；粒子字段保留但尚未接入。"),
                ToroHealthModule.instance(), ToroHealthConfigScreen::new);
        real("teammate_hp", "TeammateHP", unchanged("队友血量"), GROUP_INFO,
                revised("用屏幕卡片显示每个队友的血量、吸收盾和距离。",
                        "屏幕卡片显示队友血量、吸收盾与距离。"),
                TeammateHpModule.instance(), TeammateHpConfigScreen::new);

        real("team_sync", "TeamSync", unchanged("队伍同步"), GROUP_SQUAD,
                revised("通过一条加密的 WebSocket 把队伍位置、当前目标和手动标记同步给队友。连不上服务器不会卡住客户端。",
                        "通过安全 WebSocket 共享队伍位置、目标与手动标记；连接失败不会阻塞客户端。"),
                TeamSyncModule.instance(), TeamSyncConfigScreen::new);

        real("player_visibility", "PlayerVisibility", unchanged("玩家隐身"), GROUP_MISC,
                revised("把其他玩家隐藏或淡化，让视野更干净。",
                        "隐藏或淡化其他玩家，视野更清爽"),
                PlayerVisibilityModule.instance(), PlayerVisibilityConfigScreen::new);
        real("legacy_sneak_visuals", "LegacySneak", unchanged("潜行高度 1.8"), GROUP_MISC,
                revised("把潜行时的视觉高度抬回 1.8 的样子。只是视觉，实际碰撞没变，1.5 格的缝照样能钻过去。",
                        "潜行视觉抬回 1.8 高度（仅视觉，1.5 格缝照样能钻）。"),
                LegacySneakVisualsModule.instance(), null);
        real("toggle_sprint", "ToggleSprint", unchanged("疾跑切换"), GROUP_MISC,
                revised("一键把 Sprint 锁住，不用一直按着前进键。",
                        "一键锁定 Sprint，无需长按前进键"),
                ToggleSprintModule.instance(), ToggleSprintConfigScreen::new);
        real("chat_cleaner", "ChatCleaner", unchanged("聊天清理"), GROUP_MISC,
                revised("把重复刷屏的聊天消息合并成一条，减少刷屏。",
                        "合并重复 / 刷屏的聊天信息"),
                ChatCleanerModule.instance(), ChatCleanerConfigScreen::new);
        real("chat_copy", "ChatCopy", unchanged("聊天复制"), GROUP_MISC,
                revised("点击任意一行聊天就能复制那一行的文字。",
                        "点击聊天行即可复制其文本"),
                ChatCopyModule.instance(), null);
        real("chat_translate", "ChatTranslate", unchanged("聊天翻译"), GROUP_MISC,
                revised("把你要发的中文队聊在后台翻成 AA 常用的英文，翻译回来后自动帮你发出去。",
                        "中文队聊后台翻成 AA 英文，返回后自动发送"),
                ChatTranslateModule.instance(), ChatTranslateConfigScreen::new);
        real("chat_message_translate", "ChatMessageTranslate", unchanged("消息点击翻译"), GROUP_MISC,
                revised("聊天行尾有一个 [T] 按钮，点一下就把那条外语消息翻译成简体中文显示，原文保持不变。",
                        "聊天行尾 [T] 点击后翻译成简体中文本地显示，原文不动"),
                ChatMessageTranslateModule.instance(), null);
        real("welcome", "Welcome", unchanged("欢迎横幅"), GROUP_MISC,
                revised("进服时在聊天里打一条 MICx 提示，并告诉你 /micx 入口在哪。",
                        "进服时在聊天里显示 MICx 提示与 /micx 入口"),
                WelcomeModule.instance(), null);
        real("auto_text", "AutoText", unchanged("快捷文本"), GROUP_MISC,
                revised("给预设消息绑一个快捷键，按下就直接发到聊天。",
                        "绑定快捷键立即发送预设消息到聊天"),
                AutoTextModule.instance(), AutoTextConfigScreen::new);
        real("rank_up_tool", "RankUpTool", unchanged("求 Rank"), GROUP_CHAT,
                revised("定时把「<当前选中的 Rank> pls」发到聊天，默认 3 秒一条，大厅和局内都发；档位与间隔可以在面板里换。"
                                + "开启后打开书本界面会连响 1 分钟提醒，切到别的应用也不会自动暂停。",
                        "定时把「<选中的 Rank> pls」发到聊天（默认 3 秒一条，大厅与局内都发；面板可换档位与间隔）；"
                                + "开启后打开书本界面会连响 1 分钟提醒，切到别的应用也不会自动暂停"),
                RankUpToolModule.instance(), RankUpToolConfigScreen::new);
        real("remote_shop", "RemoteShop", revised("Reach买子弹", "远程商店"), GROUP_CORE,
                revised("不用打开商店界面就能点到远处的商店：客户端交互距离从 3 / 4.5 格放宽到 5.5 格，"
                                + "面板里还能扫描附近的全息并「买一次」离你最近的那个。注意服务端硬上限是实体 6 格 / 方块 5.5 格，超出去的包会被丢弃。",
                        "不开界面点到远处商店：客户端射程 3/4.5→5.5 格，面板可扫附近全息并「买一次」最近目标"
                                + "（服务端硬上限实体 6 格/方块 5.5 格，超了丢包）"),
                RemoteShopModule.instance(), RemoteShopConfigScreen::new);
        real("asr", "ASR", unchanged("语音输入"), GROUP_MISC,
                revised("按住 PTT 键开始录音，松开后把识别出来的文字发到聊天。",
                        "按住 PTT 录音并将识别结果发送到聊天。"),
                AsrModule.instance(), AsrConfigScreen::new);
        real("zombies_explorer", "ZombiesExplorer", unchanged("僵尸标记"), GROUP_CORE,
                revised("从 ZombiesExplorer 移植过来的标记：用线框盒加头顶标签标出 Powerup 归属怪（必出暗红 / 预测亮红）、本回合的特殊怪（绿）和线上怪（黄）。",
                        "Powerup/BadHeadshot 标记：必出红/预测粉/最后怪绿/线上黄，线框盒+头顶标签。"),
                ZombiesExplorerModule.instance(), parent -> new SimpleModuleScreen(parent,
                        ZombiesExplorerModule.instance(), UiText.shown("僵尸标记", "ZombiesExplorer"), UiText.shown("ZombiesExplorer · ZE 移植", "僵尸标记 · ZE 移植"),
                        List.of(
                                SimpleModuleScreen.Row.toggle("Powerup Detector",
                                        ZombiesExplorerModule.instance()::getPowerupDetector,
                                        ZombiesExplorerModule.instance()::setPowerupDetector,
                                        "按波次判断 Powerup 归哪只怪：一定会出的是暗红，预测的是亮红。"),
                                SimpleModuleScreen.Row.toggle("BadHeadshot Detector",
                                        ZombiesExplorerModule.instance()::getBadHeadShotDetector,
                                        ZombiesExplorerModule.instance()::setBadHeadShotDetector,
                                        "标记本回合的特殊怪（绿色）。"),
                                SimpleModuleScreen.Row.toggle("OnLine 黄线",
                                        ZombiesExplorerModule.instance()::getBadHeadShotOnLine,
                                        ZombiesExplorerModule.instance()::setBadHeadShotOnLine,
                                        "准心穿过最后一只怪时，沿线的怪标成黄色。"),
                                SimpleModuleScreen.Row.toggle(UiText.shown("头顶标签", "NameTag 标签"),
                                        ZombiesExplorerModule.instance()::getNameTag,
                                        ZombiesExplorerModule.instance()::setNameTag,
                                        "在怪头顶显示文字标签（Powerup / Bad Headshot）。"),
                                SimpleModuleScreen.Row.integer(UiText.shown("额外预测数", "Predictor 预测数"),
                                        ZombiesExplorerModule.instance()::getPowerupPredictor,
                                        v -> ZombiesExplorerModule.instance().setPowerupPredictor(v), 0, 3,
                                        "波次未知时额外多预测几只（0-3，ZE 原版的滑条）。"))));
        real("wave_spawn_sound", "WaveSpawnSound", unchanged("波次音效"), GROUP_CORE,
                revised("每波刷怪时给一声 pling 提示，最终波换成 orb 音；DE / BB 的终波前还有 3-2-1 倒计时。整套是从 SST 移植过来的。",
                        "每波刷怪 pling 提示、最终波 orb、DE/BB 终波前 3-2-1 倒计时（SST 移植）。"),
                WaveSpawnSoundModule.instance(), parent -> new SimpleModuleScreen(parent,
                        WaveSpawnSoundModule.instance(), UiText.shown("波次音效", "WaveSpawnSound"), UiText.shown("WaveSpawnSound · SST 移植", "波次音效 · SST 移植"),
                        List.of(
                                SimpleModuleScreen.Row.toggle("AA 波次音",
                                        WaveSpawnSoundModule.instance()::getAaSound,
                                        WaveSpawnSoundModule.instance()::setAaSound,
                                        "Alien Arcadium 每波的提示音。"),
                                SimpleModuleScreen.Row.toggle("DE/BB 波次音",
                                        WaveSpawnSoundModule.instance()::getDebbSound,
                                        WaveSpawnSoundModule.instance()::setDebbSound,
                                        "Dead End / Bad Blood / Lab / Prison 每波的提示音。"),
                                SimpleModuleScreen.Row.toggle("3-2-1 倒计时",
                                        WaveSpawnSoundModule.instance()::getDebbCountdown,
                                        WaveSpawnSoundModule.instance()::setDebbCountdown,
                                        "最终波到来前 3-2-1 秒各给一声 pling（默认关）。"))));
        SUBMODULES.put("zombies_assist", List.of(
                new SubmoduleDescriptor("display", "Display", unchanged("显示开关"),
                        revised("对应 Forge 版的 Display 分区：HUD、Power-up、命中统计、经济和原生 scoreboard。",
                                "Forge Display 分区：HUD、Power-up、命中统计、经济和原版 scoreboard。"),
                        ZombiesDisplayConfigScreen::new),
                new SubmoduleDescriptor("alerts", "Alerts", unchanged("提示与警报"),
                        revised("对应 Forge 版的 Alerts 分区：TOO、BLOCK、弹药、LS、FR 和威胁提示。",
                                "Forge Alerts 分区：TOO、BLOCK、弹药、LS、FR 和威胁提示。"),
                        ZombiesAlertsConfigScreen::new),
                new SubmoduleDescriptor("auto", "Auto & Chat", unchanged("记分板 / 消息"),
                        revised("对应 Forge 版的 Auto 分区：回合播报、自动提醒、赛后统计和 noRotate。",
                                "Forge Auto 分区：回合播报、自动提醒、赛后统计和 noRotate。"),
                        ZombiesAutoConfigScreen::new)));
    }

    private ModulePanelRegistry() {
    }

    private static void real(String id, String display, UiText.Txt chinese, String group,
                             UiText.Txt description, Module module,
                             java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> factory) {
        String g = META_GROUP.getOrDefault(id, group);
        int o = META_ORDER.getOrDefault(id, nextOrder);
        register(new ModulePanelDescriptor(id, display, chinese, g, o, description,
                module, factory, keybindFor(id, module)));
        nextOrder++;
    }

    private static void unmigrated(String id, String display, UiText.Txt chinese, String group,
                                   UiText.Txt description) {
        register(new ModulePanelDescriptor(id, display, chinese, group, nextOrder++, description,
                new UnmigratedModule(id), null, null, false, false));
    }

    private static void blocked(String id, String display, UiText.Txt chinese, String group,
                                UiText.Txt description) {
        int o = META_ORDER.getOrDefault(id, nextOrder);
        String g = META_GROUP.getOrDefault(id, group);
        register(new ModulePanelDescriptor(id, display, chinese, g, o, description,
                new UnmigratedModule(id), null, null, false, true));
        nextOrder++;
    }

    private static ModuleKeybind keybindFor(String id, Module module) {
        String desc = "主快捷键（最多 3 键，空为未绑定）";
        int[] chord = module.primaryChord();
        if (chord != null) {
            return new ModuleChordAdapter(id, desc, module::primaryChord, codes -> setPrimaryChord(module, codes));
        }
        // zoom_scope MUST use single-key adapter; other single-binding modules also use single-key adapter
        InputBinding single = module.primaryBinding();
        if (single != null) {
            String singleDesc = "主快捷键（单键，空为未绑定）";
            return new ModuleKeybindAdapter(id, singleDesc, module::primaryBinding, code -> setPrimarySingle(module, code));
        }
        // 无自带绑定的模块：组合键存面板侧（micx-panel-bindings.properties），HotkeyRuntime 统一触发开关
        return new ModuleChordAdapter(id, desc,
                () -> panelChord(id), codes -> setPanelChord(id, codes));
    }

    /* ---- 面板侧统一绑定：给没有自有快捷键字段的模块补“每个模块都能绑” ---- */

    private static final Map<String, int[]> PANEL_CHORDS = new LinkedHashMap<>();
    private static boolean panelChordsLoaded;

    private static void ensurePanelChordsLoaded() {
        if (panelChordsLoaded) return;
        panelChordsLoaded = true;
        Properties p = ConfigProperties.load(
                FabricRuntime.configPath().resolve("micx-panel-bindings.properties"), null);
        for (String key : p.stringPropertyNames()) {
            int[] codes = KeyChord.parse(p.getProperty(key, ""));
            if (!KeyChord.isEmpty(codes)) PANEL_CHORDS.put(key, codes);
        }
    }

    /** 面板侧为该模块保存的组合键；未绑定返回 null。 */
    public static int[] panelChord(String id) {
        ensurePanelChordsLoaded();
        return PANEL_CHORDS.get(id);
    }

    private static void setPanelChord(String id, int[] codes) {
        ensurePanelChordsLoaded();
        int[] normalized = KeyChord.normalize(codes);
        HotkeyRuntime.clearModule(id);
        if (KeyChord.isEmpty(normalized)) {
            PANEL_CHORDS.remove(id);
        } else {
            PANEL_CHORDS.put(id, normalized);
        }
        Properties p = new Properties();
        for (Map.Entry<String, int[]> entry : PANEL_CHORDS.entrySet()) {
            p.setProperty(entry.getKey(), KeyChord.format(entry.getValue()));
        }
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("micx-panel-bindings.properties"), p,
                    "MICx panel-side module bindings");
        } catch (java.io.IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save panel bindings", exception);
        }
    }

    private static void setPrimaryChord(Module module, int[] codes) {
        if (module instanceof AimbotModule m) m.config().setToggleKeyCodes(codes);
        else if (module instanceof ReviveAuraModule m) m.setToggleKeyCodes(codes);
        else if (module instanceof MagnetModule m) m.setToggleKeyCodes(codes);
        else if (module instanceof LastMobsModule m) m.setToggleKeyCodes(codes);
    }

    private static void setPrimarySingle(Module module, int code) {
        if (module instanceof PlayerVisibilityModule m) m.setKeyCode(code);
        else if (module instanceof TeammateHpModule m) m.setKeyCode(code);
        else if (module instanceof ToggleSprintModule m) m.setKeyCode(code);
        else if (module instanceof RightClickerModule m) m.setKeyCode(code);
        else if (module instanceof ViewHoldModule m) m.setKeyCode(code);
        else if (module instanceof ZoomScopeModule m) m.setKeyCode(code);
        else if (module instanceof SkillCastModule m) m.setKeyCode(code);
        else if (module instanceof KeyboardClickerModule m) m.setToggleKey(code);
        else if (module instanceof TeamSyncModule m) { m.config().toggleKeyCode = code; m.saveConfig(); }
        else if (module instanceof NoReloadModule m) m.setKeyCode(code);
        else setPrimaryChord(module, KeyChord.single(code));
    }

    public static void register(ModulePanelDescriptor descriptor) {
        if (DESCRIPTORS.putIfAbsent(descriptor.id(), descriptor) != null) {
            throw new IllegalStateException("Duplicate panel descriptor: " + descriptor.id());
        }
    }

    public static List<ModulePanelDescriptor> all() {
        List<ModulePanelDescriptor> result = new ArrayList<>(DESCRIPTORS.values());
        result.sort(Comparator.comparingInt(ModulePanelDescriptor::order)
                .thenComparing(ModulePanelDescriptor::id));
        return Collections.unmodifiableList(result);
    }

    public static List<ModulePanelDescriptor> inGroup(String group) {
        List<ModulePanelDescriptor> result = new ArrayList<>();
        for (ModulePanelDescriptor descriptor : DESCRIPTORS.values()) {
            if (descriptor.group().equals(group)) result.add(descriptor);
        }
        result.sort(Comparator.comparingInt(ModulePanelDescriptor::order)
                .thenComparing(ModulePanelDescriptor::id));
        return Collections.unmodifiableList(result);
    }

    public static ModulePanelDescriptor get(String id) {
        return DESCRIPTORS.get(id);
    }

    public static List<SubmoduleDescriptor> submodules(String parentId) {
        return SUBMODULES.getOrDefault(parentId, List.of());
    }

    public static SubmoduleDescriptor submodule(String parentId, String submoduleId) {
        for (SubmoduleDescriptor descriptor : submodules(parentId)) {
            if (descriptor.id().equals(submoduleId)) return descriptor;
        }
        return null;
    }

    public static GroupMetadata group(String id) {
        return GROUPS.getOrDefault(id, new GroupMetadata(id, unchanged(id)));
    }

    /** 分组标题：英文眉标固定，中文名走双语义。 */
    public record GroupMetadata(String displayName, UiText.Txt chineseNameText) {
        /** 中文名（面板主标题），随文案开关切换。 */
        public String chineseName() {
            return UiText.of(chineseNameText);
        }
    }

    public record SubmoduleDescriptor(
            String id,
            String displayName,
            UiText.Txt chineseNameText,
            UiText.Txt descriptionText,
            java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> configScreenFactory) {
        public boolean hasConfigScreen() {
            return configScreenFactory != null;
        }

        public net.minecraft.client.gui.screens.Screen createConfigScreen(net.minecraft.client.gui.screens.Screen parent) {
            return configScreenFactory == null ? null : configScreenFactory.apply(parent);
        }

        /** 中文名（面板主标题），随文案开关切换。 */
        public String chineseName() {
            return UiText.of(chineseNameText);
        }

        /** 分区说明，随文案开关切换。 */
        public String description() {
            return UiText.of(descriptionText);
        }
    }
}

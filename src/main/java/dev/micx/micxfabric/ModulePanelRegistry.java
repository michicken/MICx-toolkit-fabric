package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Panel metadata; runtime registration is maintained separately in ModuleRuntime. */
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

        GROUPS.put(GROUP_CORE, new GroupMetadata("CORE", "Zombies 核心"));
        GROUPS.put(GROUP_VISION, new GroupMetadata("VISION", "战斗 · 视觉"));
        GROUPS.put(GROUP_ACTION, new GroupMetadata("ACTION", "战斗 · 操作"));
        GROUPS.put(GROUP_INFO, new GroupMetadata("HUD & INFO", "信息面板"));
        GROUPS.put(GROUP_SQUAD, new GroupMetadata("SQUAD", "队伍协同"));
        GROUPS.put(GROUP_CHAT, new GroupMetadata("CHAT", "聊天"));
        GROUPS.put(GROUP_MISC, new GroupMetadata("MISC", "视觉 · 输入"));
        GROUPS.put(GROUP_DEBUG, new GroupMetadata("DEBUG", "调试"));

        real("sword_block", "SwordBlock", "剑格挡", GROUP_CORE, "客户端视觉格挡动画，不提供服务端伤害减免。",
                SwordBlockModule.instance(), SwordBlockConfigScreen::new);
        real("round_timer", "RoundTimer", "回合计时", GROUP_CORE,
                "当前回合用时 R{n} mm:ss，与 Wave Table 同区块同偏移。",
                RoundTimerModule.instance(), null);
        real("eco_rate", "EcoRate", "经济速率", GROUP_CORE,
                "右侧经济表周期性把金币切为每2分钟纯增长速率（绿字闪烁），2分钟窗口每10秒刷新。",
                EcoRateModule.instance(), EcoRatePanelScreen::new);
        real("lr_indicator", "LR Indicator", "LR 指示", GROUP_CORE,
                "AA 物品栏上方的 LR 释放清单：绿=已放 18s 内，红=未放，轮换位置 /micx lr 2/3/4。",
                LrIndicatorModule.instance(), parent -> new SimpleModuleScreen(parent,
                        LrIndicatorModule.instance(), "LR Indicator", "LR 释放清单 · 蜂鸣与偏移",
                        List.of(
                                SimpleModuleScreen.Row.toggle("Beep 蜂鸣",
                                        LrIndicatorModule.instance()::lrBeepEnabled,
                                        v -> LrIndicatorModule.instance().lrBeepEnabled = v,
                                        "LR 可释放时播放提示音。"),
                                SimpleModuleScreen.Row.integer("Offset X",
                                        () -> LrIndicatorModule.instance().lrHudDx,
                                        v -> LrIndicatorModule.instance().lrHudDx = v, -300, 300,
                                        "清单水平偏移（HUD Layout 拖动 Wave Table 不影响此处）。"),
                                SimpleModuleScreen.Row.integer("Offset Y",
                                        () -> LrIndicatorModule.instance().lrHudDy,
                                        v -> LrIndicatorModule.instance().lrHudDy = v, -300, 300,
                                        "清单垂直偏移。"))));
        real("auto_hide_visuals", "AutoHide Visuals", "自动隐藏", GROUP_DEBUG,
                "对局结算 1 分钟藏 ESP/Chams/Outline/AimLead，R1/离图恢复。",
                AutoHideVisualsModule.instance(), null);
        real("packet_log", "PacketLog", "封包日志", GROUP_DEBUG,
                "记录玩家自己这条连接的收发封包（出站交互/点格子/聊天/自定义通道，入站开界面/聊天/标题/拉回…）"
                        + "，写 config/MICxToolkit/logs/ 下的日志文件；快捷键插标记线，用来查买弹到底走哪条通道",
                PacketLogModule.instance(), PacketLogConfigScreen::new);
        real("zombie_fade", "ZombieFade", "僵尸淡化", GROUP_MISC,
                "近距离敌对生物半透明淡化。",
                ZombieFadeModule.instance(), parent -> new SimpleModuleScreen(parent,
                        ZombieFadeModule.instance(), "ZombieFade", "近距离怪物淡化",
                        List.of(
                                SimpleModuleScreen.Row.decimal("Radius 半径",
                                        ZombieFadeModule.instance()::getRadius,
                                        ZombieFadeModule.instance()::setRadius, 1, 10,
                                        "玩家周围该半径（格）内的敌对生物淡化为半透明。"),
                                SimpleModuleScreen.Row.decimal("Alpha 不透明度",
                                        ZombieFadeModule.instance()::getAlpha,
                                        ZombieFadeModule.instance()::setAlpha, 0.05, 1,
                                        "淡化后的不透明度，越小越透明（0.05~1）。"))));
        real("particle_free", "NoParticles", "粒子屏蔽", GROUP_MISC,
                "屏蔽所有粒子效果（爆炸/破坏等一切），匹配 1.8.9 的完全关闭语义。",
                ParticleFreeModule.instance(), null);
        real("revive_aura", "ReviveAura", "自动救人", GROUP_CORE,
                "队友倒地睡在附近时自动发救援交互包：每人每次倒地一包；冷却按目标分开——"
                        + "刚点过 A 不影响立刻点 B，interval 只挡对同一只的连点，每 tick 最多一包。",
                ReviveAuraModule.instance(), parent -> new SimpleModuleScreen(parent,
                        ReviveAuraModule.instance(), "ReviveAura", "自动救援发包",
                        List.of(
                                SimpleModuleScreen.Row.decimal("Range 范围",
                                        ReviveAuraModule.instance()::getRange,
                                        ReviveAuraModule.instance()::setRange, 1, 10,
                                        "与倒地队友的最大救援距离（格）。"),
                                SimpleModuleScreen.Row.decimal("Interval 间隔(ms)",
                                        ReviveAuraModule.instance()::getIntervalMs,
                                        ReviveAuraModule.instance()::setIntervalMs, 50, 1000,
                                        "对同一目标的最小重发间隔（毫秒）；冷却分目标，点过 A 不影响立刻点 B。"))));
        real("last_mobs", "LastMobs", "残怪连线", GROUP_INFO,
                "回合剩余怪 ≤N 时，准心向每只残怪拉黄色指示线（计分板权威计数）。",
                LastMobsModule.instance(), parent -> new SimpleModuleScreen(parent,
                        LastMobsModule.instance(), "LastMobs", "残怪牵线",
                        List.of(
                                SimpleModuleScreen.Row.integer("Max Count 阈值",
                                        LastMobsModule.instance()::getMaxCount,
                                        LastMobsModule.instance()::setMaxCount, 1, 10,
                                        "剩余怪物 ≤ 该值时显示连线。"),
                                SimpleModuleScreen.Row.integer("Alpha %",
                                        LastMobsModule.instance()::getLineAlphaPct,
                                        LastMobsModule.instance()::setLineAlphaPct, 20, 100,
                                        "线条不透明度百分比。"))));
        real("spawn_marker", "SpawnMarker", "刷怪点标记", GROUP_INFO,
                "AA 已知刷怪点固定灰色光柱：11 地面点 + 4 UFO 放怪口（纯预设）。",
                SpawnMarkerModule.instance(), parent -> new SimpleModuleScreen(parent,
                        SpawnMarkerModule.instance(), "SpawnMarker", "刷怪点光柱",
                        List.of(
                                SimpleModuleScreen.Row.decimal("Alpha",
                                        SpawnMarkerModule.instance()::getAlpha,
                                        v -> SpawnMarkerModule.instance().setAlpha((float) v), 0.05, 1,
                                        "标记整体透明度。"))));
        real("slime_forecast", "SlimeForecast", "史莱姆预告", GROUP_INFO,
                "刷史莱姆/岩浆波次前在 12 个固定点显示绿 X（墨绿→亮绿→隐藏），Force 模式常显。",
                SlimeForecastModule.instance(), parent -> new SimpleModuleScreen(parent,
                        SlimeForecastModule.instance(), "SlimeForecast", "史莱姆波次预告",
                        List.of(
                                SimpleModuleScreen.Row.toggle("Force 常显",
                                        SlimeForecastModule.instance()::isForceMode,
                                        SlimeForecastModule.instance()::setForceMode,
                                        "无视阶段状态常驻显示 12 个预测点。"),
                                SimpleModuleScreen.Row.decimal("Alpha",
                                        SlimeForecastModule.instance()::getAlpha,
                                        v -> SlimeForecastModule.instance().setAlpha((float) v), 0.05, 1,
                                        "X 标记透明度。"))));
        real("golem_marker", "GolemMarker", "铁傀儡标记", GROUP_INFO,
                "铁傀儡 5 个固定出生点贴地灰 X，穿墙可见。",
                GolemMarkerModule.instance(), parent -> new SimpleModuleScreen(parent,
                        GolemMarkerModule.instance(), "GolemMarker", "铁傀儡出生点 X",
                        List.of(
                                SimpleModuleScreen.Row.decimal("Alpha",
                                        GolemMarkerModule.instance()::getAlpha,
                                        v -> GolemMarkerModule.instance().setAlpha((float) v), 0.05, 1,
                                        "X 标记透明度。"))));
        real("zoom_scope", "ZoomScope", "放大镜", GROUP_MISC,
                "按住放大镜键在屏幕中央 16:9 放大 2~8x，滚轮调倍率，松开恢复，灵敏度按 k/zoom 缩放。",
                ZoomScopeModule.instance(), parent -> new ZoomScopePanelScreen(parent, ZoomScopeModule.instance()));
        real("view_hold", "ViewHold", "快捷视角", GROUP_MISC,
                "按住绑定键切到背后/正面视角，松开恢复第一人称；正面视角支持俯仰镜像（需先绑定按键）。",
                ViewHoldModule.instance(), ViewHoldConfigScreen::new);
        real("anti_reshift", "AntiReshift", "防松Shift", GROUP_MISC,
                "救援途中防误松 Shift（Type B）：不自动重按、不发包，默认关闭；低血/救起自动放行。",
                AntiReshiftModule.instance(), parent -> new SimpleModuleScreen(parent,
                        AntiReshiftModule.instance(), "AntiReshift", "救援防误松 Shift",
                        List.of(
                                SimpleModuleScreen.Row.integer("Duo 起效回合",
                                        AntiReshiftModule.instance()::getDuoStartRound,
                                        AntiReshiftModule.instance()::setDuoStartRound, 1, 100,
                                        "双排从该回合开始生效。"),
                                SimpleModuleScreen.Row.integer("3-4排 起效回合",
                                        AntiReshiftModule.instance()::getNonDuoStartRound,
                                        AntiReshiftModule.instance()::setNonDuoStartRound, 1, 100,
                                        "三排/四排从该回合开始生效。"),
                                SimpleModuleScreen.Row.decimal("低血放行 HP",
                                        AntiReshiftModule.instance()::getLowHealthHp,
                                        v -> AntiReshiftModule.instance().setLowHealthHp((float) v), 1, 20,
                                        "低于该血量暂停防松（双排忽略此规则）。"))));
        real("magnet", "Magnet 吸附", "吸附", GROUP_COMBAT,
                "按住右键时准心轻微吸向目标爆头点，手瞄快甩自动退场；仅 Zombies 生效。",
                MagnetModule.instance(), parent -> new SimpleModuleScreen(parent,
                        MagnetModule.instance(), "Magnet", "视角吸附",
                        List.of(
                                SimpleModuleScreen.Row.toggle("减速带模式",
                                        MagnetModule.instance()::isSlowMode,
                                        MagnetModule.instance()::setSlowMode,
                                        "开=对上怪物时降灵敏度（变黏）不主动拉；关=叠加牵引。"),
                                SimpleModuleScreen.Row.toggle("Hitbox Only",
                                        MagnetModule.instance()::isHitboxOnly,
                                        MagnetModule.instance()::setHitboxOnly,
                                        "减速带触发：仅射线命中 hitbox 才减速；关=按吸附半径角距。"),
                                SimpleModuleScreen.Row.toggle("Zombies Only",
                                        MagnetModule.instance()::isZombiesOnly,
                                        MagnetModule.instance()::setZombiesOnly,
                                        "仅在 Zombies 对局内生效。"),
                                SimpleModuleScreen.Row.toggle("吸 Slime",
                                        MagnetModule.instance()::isIncludeSlime,
                                        MagnetModule.instance()::setIncludeSlime,
                                        "史莱姆/岩浆怪纳入吸附目标。"),
                                SimpleModuleScreen.Row.toggle("吸 Golem",
                                        MagnetModule.instance()::isIncludeGolem,
                                        MagnetModule.instance()::setIncludeGolem,
                                        "铁傀儡纳入吸附目标。"),
                                SimpleModuleScreen.Row.toggle("吸巨人",
                                        MagnetModule.instance()::isIncludeGiant,
                                        MagnetModule.instance()::setIncludeGiant,
                                        "巨人纳入吸附目标。"),
                                SimpleModuleScreen.Row.decimal("Radius 度",
                                        MagnetModule.instance()::getRadiusDeg,
                                        MagnetModule.instance()::setRadiusDeg, 2, 30,
                                        "吸附生效的准星锥角。"),
                                SimpleModuleScreen.Row.decimal("Pull 强度",
                                        MagnetModule.instance()::getPullStrength,
                                        MagnetModule.instance()::setPullStrength, 0.02, 0.6,
                                        "每 tick 指数收敛系数，越大吸得越快。"),
                                SimpleModuleScreen.Row.decimal("Slow 减速",
                                        MagnetModule.instance()::getSlowFactor,
                                        MagnetModule.instance()::setSlowFactor, 0.1, 0.95,
                                        "接近目标时的手部减速系数。"),
                                SimpleModuleScreen.Row.decimal("Headshot 停拉",
                                        MagnetModule.instance()::getHeadshotStopDeg,
                                        MagnetModule.instance()::setHeadshotStopDeg, 0.5, 10.0,
                                        "进入爆头点该角度内停止牵引（贴脸松/远处紧）。"))));
        real("fullbright", "Fullbright", "全亮", GROUP_CORE,
                "强制 gamma 全亮，Forge Fullbright 的 Fabric 等价实现；关闭时还原。",
                FullbrightModule.instance(), null);
        real("zombies_assist", "ZombiesAssist", "僵尸助手", GROUP_CORE,
                "波次、僵尸剩余、Power-up、警报、自动行为和 Alien Arcadium 状态 HUD。",
                ZombiesAssistModule.instance(), ZombiesAssistConfigScreen::new);
        real("window_spawn_counter", "WindowSpawns", "刷怪窗口", GROUP_CORE,
                "11 窗按出生点统计的刷怪量（P1/P2/P3/P4/P5/ULT/ALT/CL/CR/BL/BR），波次内累积、跨波次清，HUD 可拖动。TOO 生成时该窗显示 T 全红，顶部 TOO IN 持续 3 波。",
                WindowSpawnCounterModule.instance(), parent -> new SimpleModuleScreen(parent,
                        WindowSpawnCounterModule.instance(), "WindowSpawns", "刷怪窗口 · TOO 警报",
                        List.of(
                                SimpleModuleScreen.Row.toggle("TOO 窗口警报",
                                        WindowSpawnCounterModule.instance()::tooWindowAlert,
                                        WindowSpawnCounterModule.instance()::setTooWindowAlert,
                                        "TOO 出生窗标红 T / TOO IN 列表 / 中央提示补窗名与米数。"),
                                SimpleModuleScreen.Row.toggle("  /pc 队内播报",
                                        WindowSpawnCounterModule.instance()::tooWindowPc,
                                        WindowSpawnCounterModule.instance()::setTooWindowPc,
                                        "按 r58/r59/r101 规则自动 /pc，最近玩家判定，每条均可单独关。"),
                                SimpleModuleScreen.Row.toggle("  本地叮声",
                                        WindowSpawnCounterModule.instance()::tooWindowSound,
                                        WindowSpawnCounterModule.instance()::setTooWindowSound,
                                        "每条紫字本地提示附 note.pling 叮声。"))));
        real("anti_axe", "AntiAXE", "防误领 Puncher", GROUP_CORE,
                "抽到 The Puncher 时锁定 Lucky Chest 领取区右键 10.5 秒，防误领；领到其他物品自动解除。",
                AntiAxeModule.instance(), parent -> new SimpleModuleScreen(parent,
                        AntiAxeModule.instance(), "AntiAXE", "Puncher 领取保护",
                        List.of(
                                SimpleModuleScreen.Row.integer("HUD Offset X",
                                        AntiAxeModule.instance()::getHudDx,
                                        AntiAxeModule.instance()::setHudDx, -300, 300,
                                        "中央提示水平偏移。"),
                                SimpleModuleScreen.Row.integer("HUD Offset Y",
                                        AntiAxeModule.instance()::getHudDy,
                                        AntiAxeModule.instance()::setHudDy, -300, 300,
                                        "中央提示垂直偏移。"))));

        real("noreload", "NoReload", "免换弹", GROUP_CORE,
                "按住右键轮换武器槽并发包重置武器状态跳过换弹动画；与 AutoSwitch 互斥，默认关闭。",
                NoReloadModule.instance(), parent -> new SimpleModuleScreen(parent,
                        NoReloadModule.instance(), "NoReload", "免换弹",
                        List.of(
                                SimpleModuleScreen.Row.decimal("Delay ms",
                                        NoReloadModule.instance()::getDelayMs,
                                        NoReloadModule.instance()::setDelayMs, 50, 80,
                                        "轮换间隔（原版默认 67ms）。"),
                                SimpleModuleScreen.Row.toggle("槽 2 参与轮换",
                                        NoReloadModule.instance()::isSlot2Enabled,
                                        NoReloadModule.instance()::setSlot2,
                                        "快捷栏第 2 格参与轮换。"),
                                SimpleModuleScreen.Row.toggle("槽 3 参与轮换",
                                        NoReloadModule.instance()::isSlot3Enabled,
                                        NoReloadModule.instance()::setSlot3,
                                        "快捷栏第 3 格参与轮换。"),
                                SimpleModuleScreen.Row.toggle("槽 4 参与轮换",
                                        NoReloadModule.instance()::isSlot4Enabled,
                                        NoReloadModule.instance()::setSlot4,
                                        "快捷栏第 4 格参与轮换。"),
                                SimpleModuleScreen.Row.toggle("RR 模式",
                                        NoReloadModule.instance()::isRrMode,
                                        NoReloadModule.instance()::setRrMode,
                                        "金铲子槽不点击背包（近战无需重置）。"))));

        real("aimbot", "Aimbot", "瞄准辅助", GROUP_COMBAT,
                "从 1.8.9 迁移的目标筛选、优先级、AimLead 头部点、鼠标接管与 Hold-Lock；默认关闭。",
                AimbotModule.instance(), AimbotConfigScreen::new);
        real("aimbot_hud", "Aimbot HUD", "Aimbot HUD", GROUP_COMBAT,
                "独立显示 TOO/Golem/Slime 忽略、Clown/Giant/Baby 优先和 Closest 状态，并处理分组快捷键。",
                AimbotHudModule.instance(), null);
        real("aim_lead", "AimLead", "瞄准提前量", GROUP_COMBAT,
                "按服务端 movement packet 轨迹预判目标位置，标出开火提前点。",
                AimLeadModule.instance(), AimLeadConfigScreen::new);
        real("esp", "ESP", "线框透视", GROUP_COMBAT,
                "透过墙壁提交非玩家实体方框轮廓，带范围、透明度和自动门控。",
                EspModule.instance(), EspConfigScreen::new);
        real("chams", "Chams", "模型透视", GROUP_COMBAT,
                "以原贴图模型穿过墙壁显示被方块遮挡的目标；独立于 ESP 线框。",
                ChamsModule.instance(), ChamsConfigScreen::new);
        real("player_outline_esp", "PlayerOutlineESP", "玩家轮廓", GROUP_COMBAT,
                "使用 26.2 原生 outline phase 的玩家绿色轮廓；厚度由客户端原生管线控制。",
                PlayerOutlineEspModule.instance(), PlayerOutlineEspConfigScreen::new);
        real("right_clicker", "RightClicker", "自动右键", GROUP_COMBAT,
                "按住时真实模拟快速右键（默认 20 CPS，每 tick 一发；面板/指令可调，与 AimLead 单引擎互让）", RightClickerModule.instance(), RightClickerConfigScreen::new);
        real("skill_cast", "SkillCast", "技能释放", GROUP_COMBAT,
                "切槽5 + 原生右键 + 切回，单次激活", SkillCastModule.instance(), SkillCastConfigScreen::new);
        real("keyboard_clicker", "KeyboardClicker", "自动切枪 AutoSwitch", GROUP_COMBAT,
                "键盘触发的自动切枪，多种组合", KeyboardClickerModule.instance(), KeyboardClickerConfigScreen::new);

        real("dps_counter", "DPSCounter", "DPS 计数", GROUP_INFO,
                "实时统计你的每秒伤害（DPS）", DpsCounterModule.instance(), DpsCounterConfigScreen::new);
        real("toro_health", "ToroHealth", "伤害数字", GROUP_INFO,
                "准心目标血量与吸收盾 HUD；粒子字段保留但尚未接入。",
                ToroHealthModule.instance(), ToroHealthConfigScreen::new);
        real("teammate_hp", "TeammateHP", "队友血量", GROUP_INFO,
                "屏幕卡片显示队友血量、吸收盾与距离。",
                TeammateHpModule.instance(), TeammateHpConfigScreen::new);

        real("team_sync", "TeamSync", "队伍同步", GROUP_SQUAD,
                "通过安全 WebSocket 共享队伍位置、目标与手动标记；连接失败不会阻塞客户端。",
                TeamSyncModule.instance(), TeamSyncConfigScreen::new);

        real("player_visibility", "PlayerVisibility", "玩家隐身", GROUP_MISC,
                "隐藏或淡化其他玩家，视野更清爽", PlayerVisibilityModule.instance(), PlayerVisibilityConfigScreen::new);
        real("legacy_sneak_visuals", "LegacySneak", "潜行高度 1.8", GROUP_MISC,
                "潜行视觉抬回 1.8 高度（仅视觉，1.5 格缝照样能钻）。", LegacySneakVisualsModule.instance(), null);
        real("toggle_sprint", "ToggleSprint", "疾跑切换", GROUP_MISC,
                "一键锁定 Sprint，无需长按前进键", ToggleSprintModule.instance(), ToggleSprintConfigScreen::new);
        real("chat_cleaner", "ChatCleaner", "聊天清理", GROUP_MISC,
                "合并重复 / 刷屏的聊天信息", ChatCleanerModule.instance(), ChatCleanerConfigScreen::new);
        real("chat_copy", "ChatCopy", "聊天复制", GROUP_MISC,
                "点击聊天行即可复制其文本", ChatCopyModule.instance(), null);
        real("chat_translate", "ChatTranslate", "聊天翻译", GROUP_MISC,
                "中文队聊后台翻成 AA 英文，返回后自动发送",
                ChatTranslateModule.instance(), ChatTranslateConfigScreen::new);
        real("chat_message_translate", "ChatMessageTranslate", "消息点击翻译", GROUP_MISC,
                "聊天行尾 [T] 点击后翻译成简体中文本地显示，原文不动",
                ChatMessageTranslateModule.instance(), null);
        real("welcome", "Welcome", "欢迎横幅", GROUP_MISC,
                "进服时在聊天里显示 MICx 提示与 /micx 入口", WelcomeModule.instance(), null);
        real("auto_text", "AutoText", "快捷文本", GROUP_MISC,
                "绑定快捷键立即发送预设消息到聊天", AutoTextModule.instance(), AutoTextConfigScreen::new);
        real("rank_up_tool", "RankUpTool", "求 Rank", GROUP_CHAT,
                "定时把「<选中的 Rank> pls」发到聊天（默认 3 秒一条，大厅与局内都发；面板可换档位与间隔）；"
                        + "开启后打开书本界面会连响 1 分钟提醒，切到别的应用也不会自动暂停",
                RankUpToolModule.instance(), RankUpToolConfigScreen::new);
        real("remote_shop", "RemoteShop", "远程商店", GROUP_CORE,
                "不开界面点到远处商店：客户端射程 3/4.5→5.5 格，面板可扫附近全息并「买一次」最近目标"
                        + "（服务端硬上限实体 6 格/方块 5.5 格，超了丢包）",
                RemoteShopModule.instance(), RemoteShopConfigScreen::new);
        real("asr", "ASR", "语音输入", GROUP_MISC,
                "按住 PTT 录音并将识别结果发送到聊天。", AsrModule.instance(), AsrConfigScreen::new);
        real("zombies_explorer", "ZombiesExplorer", "僵尸标记", GROUP_CORE,
                "Powerup/BadHeadshot 标记：必出红/预测粉/最后怪绿/线上黄，线框盒+头顶标签。",
                ZombiesExplorerModule.instance(), parent -> new SimpleModuleScreen(parent,
                        ZombiesExplorerModule.instance(), "ZombiesExplorer", "僵尸标记 · ZE 移植",
                        List.of(
                                SimpleModuleScreen.Row.toggle("Powerup Detector",
                                        ZombiesExplorerModule.instance()::getPowerupDetector,
                                        ZombiesExplorerModule.instance()::setPowerupDetector,
                                        "按波次识别 Powerup 归属怪，必出暗红/预测亮红。"),
                                SimpleModuleScreen.Row.toggle("BadHeadshot Detector",
                                        ZombiesExplorerModule.instance()::getBadHeadShotDetector,
                                        ZombiesExplorerModule.instance()::setBadHeadShotDetector,
                                        "标记本回合特殊怪（绿）。"),
                                SimpleModuleScreen.Row.toggle("OnLine 黄线",
                                        ZombiesExplorerModule.instance()::getBadHeadShotOnLine,
                                        ZombiesExplorerModule.instance()::setBadHeadShotOnLine,
                                        "准心穿过最后怪时沿线怪标黄。"),
                                SimpleModuleScreen.Row.toggle("NameTag 标签",
                                        ZombiesExplorerModule.instance()::getNameTag,
                                        ZombiesExplorerModule.instance()::setNameTag,
                                        "头顶文字标签（Powerup / Bad Headshot）。"),
                                SimpleModuleScreen.Row.integer("Predictor 预测数",
                                        ZombiesExplorerModule.instance()::getPowerupPredictor,
                                        v -> ZombiesExplorerModule.instance().setPowerupPredictor(v), 0, 3,
                                        "未知波次时额外预测几只（0-3，ZE 原版滑条）。"))));
        real("wave_spawn_sound", "WaveSpawnSound", "波次音效", GROUP_CORE,
                "每波刷怪 pling 提示、最终波 orb、DE/BB 终波前 3-2-1 倒计时（SST 移植）。",
                WaveSpawnSoundModule.instance(), parent -> new SimpleModuleScreen(parent,
                        WaveSpawnSoundModule.instance(), "WaveSpawnSound", "波次音效 · SST 移植",
                        List.of(
                                SimpleModuleScreen.Row.toggle("AA 波次音",
                                        WaveSpawnSoundModule.instance()::getAaSound,
                                        WaveSpawnSoundModule.instance()::setAaSound,
                                        "Alien Arcadium 每波提示音。"),
                                SimpleModuleScreen.Row.toggle("DE/BB 波次音",
                                        WaveSpawnSoundModule.instance()::getDebbSound,
                                        WaveSpawnSoundModule.instance()::setDebbSound,
                                        "Dead End / Bad Blood / Lab / Prison 每波提示音。"),
                                SimpleModuleScreen.Row.toggle("3-2-1 倒计时",
                                        WaveSpawnSoundModule.instance()::getDebbCountdown,
                                        WaveSpawnSoundModule.instance()::setDebbCountdown,
                                        "终波前 3-2-1 秒倒计时 pling（默认关）。"))));
        blocked("swing_chat", "SwingChat", "SwingChat", GROUP_MISC,
                "26.2/GLFW 原生支持系统输入法，Forge 的 Swing 外部输入框（LWJGL2 IME 变通）不再需要。");
        blocked("auto_reshift", "AutoReShift", "自动 Re-Shift", GROUP_MISC,
                "原 Forge 版本因反作弊封禁风险停用，Fabric 端不会启用；防误松需求由 anti_reshift（AntiReshift）覆盖。");
        blocked("ac_test_logger", "ACTestLogger", "AC 测试日志", GROUP_MISC,
                "原 Forge 版本是测试/诊断模块，Fabric 端不会启用。");

        SUBMODULES.put("zombies_assist", List.of(
                new SubmoduleDescriptor("display", "Display", "显示开关",
                        "Forge Display 分区：HUD、Power-up、命中统计、经济和原版 scoreboard。",
                        ZombiesDisplayConfigScreen::new),
                new SubmoduleDescriptor("alerts", "Alerts", "提示与警报",
                        "Forge Alerts 分区：TOO、BLOCK、弹药、LS、FR 和威胁提示。",
                        ZombiesAlertsConfigScreen::new),
                new SubmoduleDescriptor("auto", "Auto & Chat", "记分板 / 消息",
                        "Forge Auto 分区：回合播报、自动提醒、赛后统计和 noRotate。",
                        ZombiesAutoConfigScreen::new)));
    }

    private ModulePanelRegistry() {
    }

    private static void real(String id, String display, String chinese, String group,
                             String description, Module module,
                             java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> factory) {
        String g = META_GROUP.getOrDefault(id, group);
        int o = META_ORDER.getOrDefault(id, nextOrder);
        register(new ModulePanelDescriptor(id, display, chinese, g, o, description,
                module, factory, keybindFor(id, module)));
        nextOrder++;
    }

    private static void unmigrated(String id, String display, String chinese, String group,
                                   String description) {
        register(new ModulePanelDescriptor(id, display, chinese, group, nextOrder++, description,
                new UnmigratedModule(id), null, null, false, false));
    }

    private static void blocked(String id, String display, String chinese, String group,
                                String description) {
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
        return GROUPS.getOrDefault(id, new GroupMetadata(id, id));
    }

    public record GroupMetadata(String displayName, String chineseName) {
    }

    public record SubmoduleDescriptor(
            String id,
            String displayName,
            String chineseName,
            String description,
            java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> configScreenFactory) {
        public boolean hasConfigScreen() {
            return configScreenFactory != null;
        }

        public net.minecraft.client.gui.screens.Screen createConfigScreen(net.minecraft.client.gui.screens.Screen parent) {
            return configScreenFactory == null ? null : configScreenFactory.apply(parent);
        }
    }
}

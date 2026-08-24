package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Panel metadata; runtime registration is maintained separately in ModuleRuntime. */
public final class ModulePanelRegistry {
    public static final String GROUP_CORE = "core";
    public static final String GROUP_COMBAT = "combat";
    public static final String GROUP_INFO = "info";
    public static final String GROUP_SQUAD = "squad";
    public static final String GROUP_MISC = "misc";
    public static final List<String> GROUP_ORDER = List.of(
            GROUP_CORE, GROUP_COMBAT, GROUP_INFO, GROUP_SQUAD, GROUP_MISC);

    private static final Map<String, GroupMetadata> GROUPS = new LinkedHashMap<>();
    private static final Map<String, ModulePanelDescriptor> DESCRIPTORS = new LinkedHashMap<>();
    private static final Map<String, List<SubmoduleDescriptor>> SUBMODULES = new LinkedHashMap<>();
    private static int nextOrder;

    static {
        GROUPS.put(GROUP_CORE, new GroupMetadata("CORE", "Alien Arcadium 核心"));
        GROUPS.put(GROUP_COMBAT, new GroupMetadata("COMBAT", "战斗辅助"));
        GROUPS.put(GROUP_INFO, new GroupMetadata("HUD & INFO", "信息面板"));
        GROUPS.put(GROUP_SQUAD, new GroupMetadata("SQUAD", "队伍协同"));
        GROUPS.put(GROUP_MISC, new GroupMetadata("MISC", "视觉 · 输入"));

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
        real("auto_hide_visuals", "AutoHide Visuals", "自动隐藏", GROUP_MISC,
                "对局结算 1 分钟藏 ESP/Chams/Outline/AimLead，R1/离图恢复。",
                AutoHideVisualsModule.instance(), null);
        real("zombie_fade", "ZombieFade", "僵尸淡化", GROUP_MISC,
                "近距离敌对生物半透明淡化。",
                ZombieFadeModule.instance(), parent -> new SimpleModuleScreen(parent,
                        ZombieFadeModule.instance(), "ZombieFade", "近距离怪物淡化",
                        List.of(
                                SimpleModuleScreen.Row.decimal("Radius 半径",
                                        ZombieFadeModule.instance()::getRadius,
                                        ZombieFadeModule.instance()::setRadius, 1, 10,
                                        "玩家周围该半径（格）内的敌对生物淡化为半透明。"))));
        real("revive_aura", "ReviveAura", "自动救人", GROUP_CORE,
                "队友倒地睡在附近时自动发送救援交互包（纯发包，范围/间隔可配）。",
                ReviveAuraModule.instance(), parent -> new SimpleModuleScreen(parent,
                        ReviveAuraModule.instance(), "ReviveAura", "自动救援发包",
                        List.of(
                                SimpleModuleScreen.Row.decimal("Range 范围",
                                        ReviveAuraModule.instance()::getRange,
                                        ReviveAuraModule.instance()::setRange, 1, 10,
                                        "与倒地队友的最大救援距离（格）。"),
                                SimpleModuleScreen.Row.decimal("Interval 间隔ms",
                                        ReviveAuraModule.instance()::getIntervalMs,
                                        ReviveAuraModule.instance()::setIntervalMs, 50, 1000,
                                        "两次救援包之间的最小间隔。"))));
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
        real("view_hold", "ViewHold", "快捷视角", GROUP_MISC,
                "按住绑定键切到背后/正面视角，松开恢复第一人称（需先绑定按键）。",
                ViewHoldModule.instance(), parent -> new SimpleModuleScreen(parent,
                        ViewHoldModule.instance(), "ViewHold", "按住切视角",
                        List.of(
                                SimpleModuleScreen.Row.integer("Target View",
                                        ViewHoldModule.instance()::getTargetView,
                                        ViewHoldModule.instance()::setTargetView, 0, 2,
                                        "0=背后第三人称 1=正面第三人称 2=第一人称。"))));
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
                                        "接近目标时的手部减速系数。"))));
        real("fullbright", "Fullbright", "全亮", GROUP_CORE,
                "强制 gamma 全亮，Forge Fullbright 的 Fabric 等价实现；关闭时还原。",
                FullbrightModule.instance(), null);
        real("zombies_assist", "ZombiesAssist", "僵尸助手", GROUP_CORE,
                "波次、僵尸剩余、Power-up、警报、自动行为和 Alien Arcadium 状态 HUD。",
                ZombiesAssistModule.instance(), ZombiesAssistConfigScreen::new);
        unmigrated("anti_axe", "AntiAXE", "防误领 Puncher", GROUP_CORE,
                "抽到 The Puncher 时锁定 Lucky Chest 领取区右键");

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
        real("toggle_sprint", "ToggleSprint", "疾跑切换", GROUP_MISC,
                "一键锁定 Sprint，无需长按前进键", ToggleSprintModule.instance(), ToggleSprintConfigScreen::new);
        real("chat_cleaner", "ChatCleaner", "聊天清理", GROUP_MISC,
                "合并重复 / 刷屏的聊天信息", ChatCleanerModule.instance(), ChatCleanerConfigScreen::new);
        real("chat_copy", "ChatCopy", "聊天复制", GROUP_MISC,
                "点击聊天行即可复制其文本", ChatCopyModule.instance(), null);
        real("chat_translate", "ChatTranslate", "聊天翻译", GROUP_MISC,
                "中文队聊后台翻成 AA 英文，返回后自动发送",
                ChatTranslateModule.instance(), ChatTranslateConfigScreen::new);
        real("welcome", "Welcome", "欢迎横幅", GROUP_MISC,
                "进服时在聊天里显示 MICx 提示与 /micx 入口", WelcomeModule.instance(), null);
        real("auto_text", "AutoText", "快捷文本", GROUP_MISC,
                "绑定快捷键立即发送预设消息到聊天", AutoTextModule.instance(), AutoTextConfigScreen::new);
        real("asr", "ASR", "语音输入", GROUP_MISC,
                "按住 PTT 录音并将识别结果发送到聊天。", AsrModule.instance(), AsrConfigScreen::new);
        unmigrated("swing_chat", "SwingChat", "SwingChat", GROUP_MISC,
                "聊天输入与挥动动画辅助");
        blocked("auto_reshift", "AutoReShift", "自动 Re-Shift", GROUP_MISC,
                "原 Forge 版本因反作弊封禁风险停用，Fabric 端不会启用。");
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
        register(new ModulePanelDescriptor(id, display, chinese, group, nextOrder++, description,
                module, factory, keybindFor(id, module)));
    }

    private static void unmigrated(String id, String display, String chinese, String group,
                                   String description) {
        register(new ModulePanelDescriptor(id, display, chinese, group, nextOrder++, description,
                new UnmigratedModule(id), null, null, false, false));
    }

    private static void blocked(String id, String display, String chinese, String group,
                                String description) {
        register(new ModulePanelDescriptor(id, display, chinese, group, nextOrder++, description,
                new UnmigratedModule(id), null, null, false, true));
    }

    private static ModuleKeybind keybindFor(String id, Module module) {
        return switch (id) {
            case "player_visibility" -> new ModuleKeybindAdapter(id, "Forge 主快捷键",
                    module::primaryBinding, code -> PlayerVisibilityModule.instance().setKeyCode(code));
            case "teammate_hp" -> new ModuleKeybindAdapter(id, "Forge 主快捷键",
                    module::primaryBinding, code -> TeammateHpModule.instance().setKeyCode(code));
            case "toggle_sprint" -> new ModuleKeybindAdapter(id, "Forge 主快捷键",
                    module::primaryBinding, code -> ToggleSprintModule.instance().setKeyCode(code));
            case "right_clicker" -> new ModuleKeybindAdapter(id, "Forge 主快捷键",
                    module::primaryBinding, code -> RightClickerModule.instance().setKeyCode(code));
            case "view_hold" -> new ModuleKeybindAdapter(id, "Forge 主快捷键",
                    module::primaryBinding, code -> ViewHoldModule.instance().setKeyCode(code));
            case "skill_cast" -> new ModuleKeybindAdapter(id, "Forge 主快捷键",
                    module::primaryBinding, code -> SkillCastModule.instance().setKeyCode(code));
            case "keyboard_clicker" -> new ModuleKeybindAdapter(id, "Forge 主快捷键",
                    module::primaryBinding, code -> KeyboardClickerModule.instance().setToggleKey(code));
            case "team_sync" -> new ModuleKeybindAdapter(id, "Forge 主快捷键",
                    module::primaryBinding, code -> {
                        TeamSyncModule.instance().config().toggleKeyCode = code;
                        TeamSyncModule.instance().saveConfig();
                    });
            default -> null;
        };
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

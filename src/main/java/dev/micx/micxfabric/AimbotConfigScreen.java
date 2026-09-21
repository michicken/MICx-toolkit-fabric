package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Aimbot 设置面板：把触发、筛选、旋转策略和 HUD 分组展示，并保留 1.8.9 的快捷键迁移。 */
public final class AimbotConfigScreen extends ModuleConfigScreen {
    private final AimbotModule module = AimbotModule.instance();
    private final List<ToggleHit> toggleHits = new ArrayList<>();
    private final List<ModeHit> modeHits = new ArrayList<>();
    private final List<KeyHit> keyHits = new ArrayList<>();
    private int listeningIndex = -1;
    private int[] capturedKeys = new int[KeyChord.MAX_KEYS];
    private int capturedCount;

    private EditBox threatDistBox;
    private EditBox fovBox;
    private EditBox maxStepBox;
    private EditBox bruteMaxStepBox;
    private EditBox maxDistBox;
    private EditBox critsBox;
    private EditBox vcritsBox;
    private EditBox headFracMaxBox;
    private EditBox badHeadshotFracBox;
    private EditBox giantAimFracBox;
    private EditBox serverDimsScaleBox;
    private EditBox immortalLrWindowBox;
    private EditBox joystickSensitivityBox;
    private EditBox joystickSwitchBox;
    private EditBox flickSpeedBox;
    private EditBox flickExitBox;
    private EditBox humanPeakBox;
    private EditBox humanOvershootBox;
    private EditBox humanCorrBox;
    private EditBox humanMaxErrBox;
    private EditBox humanRepullBox;
    private EditBox faceUpDistBox;
    private EditBox pitchHorizonMarginBox;
    private EditBox pitchHoldToleranceBox;
    private EditBox midFallSpeedBox;
    private EditBox aboveHeightBox;
    private EditBox sweepMinRoundBox;
    private EditBox bruteSweepMinRoundBox;
    private EditBox bruteSweepChainBox;
    private EditBox bruteSweepDwellBox;
    private EditBox bruteRotationWindowBox;

    public AimbotConfigScreen(Screen parent) {
        super(parent, UiText.shown("自动瞄准", "Aimbot"), UiText.shown("Aimbot · 目标筛选 / 攻击点 / 鼠标策略", "目标筛选 · AimLead 攻击点 · 三态瞄准 · 鼠标策略"));
    }

    @Override
    protected void rebuildWidgets() {
        AimbotConfig c = module.config();
        threatDistBox = box("threatDist", Integer.toString(c.threatDist));
        fovBox = box("fov", Integer.toString(c.fov));
        maxStepBox = box("maxDegPerTick", Integer.toString(c.maxDegPerTick));
        bruteMaxStepBox = box("bruteMaxDegPerTick", Integer.toString(c.bruteMaxDegPerTick));
        maxDistBox = box("maxDist", Integer.toString(c.maxDist));
        critsBox = box("crits", Double.toString(c.crits));
        vcritsBox = box("vcrits", Double.toString(c.vcrits));
        headFracMaxBox = box("headFracMax", Double.toString(c.headFracMax));
        badHeadshotFracBox = box("badHeadshotFrac", Double.toString(c.badHeadshotFrac));
        giantAimFracBox = box("giantAimFrac", Double.toString(c.giantAimFrac));
        serverDimsScaleBox = box("serverDimsScale", Double.toString(c.serverDimsScale));
        immortalLrWindowBox = box("immortalLrWindowSec", Integer.toString(c.immortalLrWindowSec));
        joystickSensitivityBox = box("joystickSensitivity", Double.toString(c.joystickSensitivity));
        joystickSwitchBox = box("joystickSwitchDeg", Integer.toString(c.joystickSwitchDeg));
        flickSpeedBox = box("joystickFlickPxPerSec", Integer.toString(c.joystickFlickPxPerSec));
        flickExitBox = box("joystickFlickExitMs", Integer.toString(c.joystickFlickExitMs));
        humanPeakBox = box("humanizePeakDeg", Integer.toString(c.humanizePeakDeg));
        humanOvershootBox = box("humanizeOvershoot", Double.toString(c.humanizeOvershoot));
        humanCorrBox = box("humanizeCorrMs", Integer.toString(c.humanizeCorrMs));
        humanMaxErrBox = box("humanizeMaxErrDeg", Double.toString(c.humanizeMaxErrDeg));
        humanRepullBox = box("humanizeRepullDeg", Double.toString(c.humanizeRepullDeg));
        faceUpDistBox = box("faceUpDist", Double.toString(c.faceUpDist));
        pitchHorizonMarginBox = box("pitchHorizonMarginDeg", Double.toString(c.pitchHorizonMarginDeg));
        pitchHoldToleranceBox = box("pitchHoldToleranceDeg", Double.toString(c.pitchHoldToleranceDeg));
        midFallSpeedBox = box("midFallSpeed", Double.toString(c.midFallSpeed));
        aboveHeightBox = box("aboveHeightBlocks", Double.toString(c.aboveHeightBlocks));
        sweepMinRoundBox = box("sweepMinRound", Integer.toString(c.sweepMinRound));
        bruteSweepMinRoundBox = box("bruteSweepMinRound", Integer.toString(c.bruteSweepMinRound));
        bruteSweepChainBox = box("bruteSweepChain", Double.toString(c.bruteSweepChainDeg));
        bruteSweepDwellBox = box("bruteSweepDwell", Integer.toString(c.bruteSweepDwellMs));
        bruteRotationWindowBox = box("bruteRotationWindow", Integer.toString(c.bruteRotationWindowMs));
    }

    private EditBox box(String name, String value) {
        EditBox box = new EditBox(font, 0, 0, 84, 18, Component.literal(name));
        box.setMaxLength(12);
        box.setValue(value);
        box.setBordered(true);
        addRenderableWidget(box);
        return box;
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        toggleHits.clear();
        modeHits.clear();
        keyHits.clear();
        AimbotConfig c = module.config();

        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        y = toggleRow(graphics, mouseX, mouseY, "启用 Aimbot / Enable", module.enabled(),
                () -> module.setEnabled(!module.enabled()), y,
                "打开后才会扫描实体并接管视角；关闭时只保留配置，不会自动瞄准。默认关闭，避免进世界后误接管。 ");
        y = wrapped(graphics, UiText.shown("这个模块只改客户端视角，不会自己构造攻击包；目标仍然要过 AimLead 轨迹、视野/穿透和实体过滤这几道检查。", "工作边界：模块只写客户端视角，不直接构造攻击包；目标仍必须通过 AimLead 轨迹、视野/穿透和实体过滤。"),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        section(graphics, UiText.shown("瞄准模式", "AIM STYLE / 瞄准模式"), y);
        y += 20;
        y = modeRow(graphics, mouseX, mouseY, "瞄准模式 / Aim Style", aimStyle(c),
                () -> cycleAimStyle(c), y,
                "点击右侧按钮循环：HUMANIZE 拟人 → NORMAL 普通 → BRUTE 暴力；三态互斥。");
        y = wrapped(graphics,
                "拟人模式会加入平滑、微小误差和自然换目标；普通模式只做平滑锁定；暴力模式立即换目标并使用 Brute Step，并在达到「暴力扫射起始回合」后启用 BRUTE 专用扫射（FOV 内逐个精准锁定 + 超快速切换）。三种模式都继续遵守目标筛选、AimLead、穿透和特殊 pitch 规则。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        section(graphics, UiText.shown("触发条件", "TRIGGER / 触发"), y);
        y += 20;
        y = toggleRow(graphics, mouseX, mouseY, "仅右键触发 / Only Fire", c.onlyFire,
                () -> c.onlyFire = !c.onlyFire, y,
                "仅在原版右键按下时运行瞄准；关闭后只要模块启用且处于有效世界，就可以运行。 ");
        y = toggleRow(graphics, mouseX, mouseY, "按键锁定 / Hold-Lock", c.holdLock,
                () -> c.holdLock = !c.holdLock, y,
                "按住绑定组合键时锁定并自动触发原版右键；开启后优先于 Only Fire。 ");
        y = keyRow(graphics, mouseX, mouseY, "锁定按键 / Hold-Lock Key", c::getHoldLockKeyCodes,
                c::setHoldLockKeyCodes, y,
                "左键录入，右键清空，最多 3 个键；未绑定时 Hold-Lock 不会触发。 ");
        y = toggleRow(graphics, mouseX, mouseY, "仅 Zombies / Zombies Only", c.zombiesOnly,
                () -> c.zombiesOnly = !c.zombiesOnly, y,
                "开启后只在 Zombies 会话扫描目标；关闭后允许扫描其他支持的生物实体。 ");

        section(graphics, UiText.shown("目标筛选", "TARGET / 目标筛选"), y);
        y += 20;
        y = toggleRow(graphics, mouseX, mouseY, "忽略 TOO / Ignore TOO", c.ignoreToo,
                () -> c.ignoreToo = !c.ignoreToo, y,
                "开启后完全跳过 TOO 目标；关闭后按普通目标规则参与筛选。 ");
        y = keyRow(graphics, mouseX, mouseY, "TOO 快捷键 / Ignore TOO Key", c::getIgnoreTooKey,
                c::setIgnoreTooKey, y, "左键录入切换键，右键清空；按键只改变该筛选项，不会直接开关 Aimbot。 ");
        y = toggleRow(graphics, mouseX, mouseY, "忽略铁傀儡 / Ignore Golem", c.ignoreGolem,
                () -> c.ignoreGolem = !c.ignoreGolem, y,
                "开启后不扫描 Iron Golem；适合不希望把傀儡加入目标队列的场景。 ");
        y = keyRow(graphics, mouseX, mouseY, "傀儡快捷键 / Ignore Golem Key", c::getIgnoreGolemKey,
                c::setIgnoreGolemKey, y, "绑定后可在游戏中快速切换“忽略铁傀儡”。 ");
        y = toggleRow(graphics, mouseX, mouseY, "忽略史莱姆 / Ignore Slime", c.ignoreSlime,
                () -> c.ignoreSlime = !c.ignoreSlime, y,
                "开启后跳过 Slime 和 Magma Cube；关闭后它们可以正常进入目标筛选。 ");
        y = keyRow(graphics, mouseX, mouseY, "史莱姆快捷键 / Ignore Slime Key", c::getIgnoreSlimeKey,
                c::setIgnoreSlimeKey, y, "绑定后可快速切换史莱姆过滤。 ");
        y = toggleRow(graphics, mouseX, mouseY, "忽略垂直下坠 / Ignore Vertical Fall", c.ignoreVerticalFall,
                () -> c.ignoreVerticalFall = !c.ignoreVerticalFall, y,
                "开启后忽略只在垂直方向快速下坠的目标，减少无效锁定和视角向下拉动。 ");
        y = toggleRow(graphics, mouseX, mouseY, "忽略 mid 高空坠怪 / Ignore Mid Fall", c.ignoreMidFall,
                () -> c.ignoreMidFall = !c.ignoreMidFall, y,
                "只在 Alien Arcadium 生效：跳过 mid 花坛（飞碟四口正下方 x∈[-3,3]、z∈[11,15]）内高速自由落体、且脚底仍高于 y=76 的怪。被打飞（水平速度大）和从窗户掉下来的怪都会被保留。 ");
        y = numberRow(graphics, "下坠速度阈值 / Mid Fall Speed", "0.5–4 格/tick", midFallSpeedBox, y);
        y = toggleRow(graphics, mouseX, mouseY, "忽略头顶高处 / Ignore Above", c.ignoreAbovePlayer,
                () -> c.ignoreAbovePlayer = !c.ignoreAbovePlayer, y,
                "开启后跳过脚底比玩家高出阈值以上的目标（高台、屋顶、轨道上够不着的怪）。恶魂 Ghast 是飞行怪，一律豁免不受此限；第 21 回合整条豁免（飞碟/高处投放怪密集），阈值不变。 ");
        y = numberRow(graphics, "高度差阈值 / Above Height", "1–32 格", aboveHeightBox, y);
        y = toggleRow(graphics, mouseX, mouseY, "无敌怪需 LR 窗口 / Immortal Needs LR", c.immortalLrGate,
                () -> c.immortalLrGate = !c.immortalLrGate, y,
                "只有 LR 会造成无敌怪：开启后仅在最近一段时间内释放过 LR 时才判定无敌怪——"
                        + "破窗的怪、靠近准备扔炸弹的 Clown 都是「站着不动又长时间不吃真实伤害」，不会再被误判成不可逆无敌怪。需要 LR Indicator 模块开着（释放时刻由它记录）。 ");
        y = numberRow(graphics, "LR 判定窗口 / LR Window", "5–30 秒", immortalLrWindowBox, y);
        y = toggleRow(graphics, mouseX, mouseY, "优先小丑 / Prio Clown", c.prioClown,
                () -> c.setPrioClown(!c.prioClown), y,
                "开启后小丑进首选档，同时把巨人压到末位档：先清小丑和小怪，小怪全不可打时才锁巨人（并自动关闭巨人优先）。 ");
        y = keyRow(graphics, mouseX, mouseY, "小丑快捷键 / Prio Clown Key", c::getPrioClownKey,
                c::setPrioClownKey, y, "绑定后可快速切换小丑优先。 ");
        y = toggleRow(graphics, mouseX, mouseY, "优先巨人 / Prio Giant", c.prioGiant,
                () -> c.setPrioGiant(!c.prioGiant), y,
                "开启后巨人进首选档，场上有巨人就先锁巨人（并自动关闭 Clown 优先）。暴力模式适用。 ");
        y = keyRow(graphics, mouseX, mouseY, "巨人快捷键 / Prio Giant Key", c::getPrioGiantKey,
                c::setPrioGiantKey, y, "绑定后可快速切换巨人优先。 ");
        y = wrapped(graphics,
                "2026-09-19 定稿：Baby 僵尸按「忽略」处理——与 TOO/傀儡/史莱姆同为最后档，场上只剩它们（或前面的怪全不可打）时才锁。BRUTE 扫射生效时也不再提前；SR 窗优先开启时窗内 Baby 仍随窗怪先清。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;
        y = modeRow(graphics, mouseX, mouseY, "SR 模式 / SR Window Priority",
                AimbotRules.windowPriorityName(c.windowPriorityMode),
                () -> c.windowPriorityMode = (c.windowPriorityMode + 1) % (AimbotRules.WP_ALT + 1),
                y,
                "专打指定出生点出来的怪（点击循环：Off → P2+P3+P4 → P5+MID → P1+ULT → ALT；HUD 简写 P234/P5M/P1U/ALT）。仅 Alien Arcadium 生效。六档选靶：① 最高档（P5+MID 的巨人 / P234 的 RC-G 傀儡）② 本模式窗怪（P5+MID=P5 窗+UFO 4 口 MID，P1+ULT=P1/ULT 窗）③ 本模式傀儡（P234=ENT-G1/ENT-G2，P1+ULT=ULT-G；另保留锚点圈兜底）④ 普通怪 ⑤ 降级（高处怪/Clown 模式巨人）⑥ 忽略（baby/TOO/傀儡/史莱姆，最后可打）。铁傀儡按出生点打标（4 个固定刷点：RC-G/ULT-G/ENT-G1/ENT-G2，出生即打标、随怪存活不掉档）。扫射门控：P234 的 P2/P3/P4 怪出生 3 秒内不扫射（集火同一只）、P1+ULT 的 P1/ULT 怪 2 秒，之后恢复扫射；其他怪不受影响。窗怪没清完绝不锁窗外的怪；被墙挡光先打其他怪，露头自动切回。可用快捷键（下方）按一下循环切换，HUD 实时显示当前模式。");
        y = keyRow(graphics, mouseX, mouseY, "SR 模式快捷键 / SR Mode Key", c::getSrModeKey,
                c::setSrModeKey, y, "绑定后按一下切到下一个 SR 模式（Off → P234 → P5M → P1U → ALT → Off 循环），聊天栏与 Aimbot HUD 实时显示当前模式。");
        y = toggleRow(graphics, mouseX, mouseY, "近身威胁 / Threat", c.threatEnabled,
                () -> c.threatEnabled = !c.threatEnabled, y,
                "开启后近距离威胁目标会被保送，并且可以无视 FOV 限制；受伤记录保留约 5 秒。 ");
        y = numberRow(graphics, "威胁距离 / Threat Dist", "3–10 格", threatDistBox, y);
        y = toggleRow(graphics, mouseX, mouseY, "视角优先 / Nearest First", c.nearestFirst,
                () -> c.nearestFirst = !c.nearestFirst, y,
                "目标等级相同且未触发 Joystick 切换时，优先保持与当前视线方向更接近的目标。 ");
        y = toggleRow(graphics, mouseX, mouseY, "距离优先 / Closest", c.closest,
                () -> c.closest = !c.closest, y,
                "在威胁排序之后按 3D 距离优先；切换会保留约 3 格缓冲，减少同级目标来回抢锁。 ");
        y = keyRow(graphics, mouseX, mouseY, "距离快捷键 / Closest Key", c::getClosestKey,
                c::setClosestKey, y, "绑定后可快速切换距离优先。 ");

        section(graphics, UiText.shown("瞄准参数", "AIM / 瞄准参数"), y);
        y += 20;
        y = toggleRow(graphics, mouseX, mouseY, "预测瞄准 / Aim Lead", c.aimLead,
                () -> c.aimLead = !c.aimLead,
                y, "读取 AimLead 的预测 AABB 和攻击点；关闭、没有有效轨迹或预测点不可见时不会瞄准。 ");
        y = toggleRow(graphics, mouseX, mouseY, "楼梯穿透 / WS Stair", c.wsStair,
                () -> c.wsStair = !c.wsStair, y,
                "控制墙体检测是否允许楼梯类方块作为可穿透路径；半砖规则仍单独处理。注意：橡木栅栏门（oak_fence_gate）、铁活板门（iron_trapdoor）、黏土块（clay）与树叶（*_leaves）已固定为不可穿透，本开关不影响它们；木种楼梯（含去皮 stripped_*）与砂岩系、地狱砖楼梯同样固定不可穿透。不可穿透的方块都按整格（1×1×1）遮挡弹道：楼梯是 L 形、台阶右上角是空的，按形状判会让射线从空角钻过去锁到实际打不到的目标，所以直接整格挡，铁活板门薄板同理；草 / 花 / 火把 / 地毯这类小形状方块不在此列，照旧可穿。射线只要碰到一个可穿透格，它后面的方块（哪怕本身不可穿）也一并放行。 ");
        y = numberRow(graphics, "视野范围 / FOV", "30–360°", fovBox, y);
        y = numberRow(graphics, "普通步长 / Max Step", "5–90°/tick", maxStepBox, y);
        y = numberRow(graphics, "暴力步长 / Brute Step", "30–180°/tick", bruteMaxStepBox, y);
        y = numberRow(graphics, "最大距离 / Max Dist", "10–400 格", maxDistBox, y);
        y = toggleRow(graphics, mouseX, mouseY, "固定腰腹 / Insta", c.insta,
                () -> c.insta = !c.insta, y, "手动常开：非巨人目标固定瞄幽灵框 0.5，即腰腹中点（不是头）；开启后按 Crits 计算的爆头点失效。 ");
        y = toggleRow(graphics, mouseX, mouseY, "自动秒杀窗口 / Auto Insta", c.autoInsta,
                () -> c.autoInsta = !c.autoInsta, y, "聊天栏报出 Insta Kill 激活后自动进入 Insta 瞄点，随道具计时结束自动退出（默认开启）。窗口内不打恶魂与巨人，并优先打移速慢、好瞄的非 baby 怪；瞄点同样支持上下兜底。 ");
        y = numberRow(graphics, "水平头系数 / Crits", "-0.2–0.5", critsBox, y);
        y = numberRow(graphics, "垂直头系数 / VCrits", "0–1", vcritsBox, y);
        y = numberRow(graphics, "头部上限 / Head Clamp", "0.01–2", headFracMaxBox, y);
        y = numberRow(graphics, "坏爆头瞄点 / BadHS Frac", "0.30–0.95", badHeadshotFracBox, y);
        y = wrapped(graphics,
                "BadHeadShot 怪（站在你上方的僵尸/骷髅）单独用的瞄准高度系数，作用在幽灵框上。爆头带下沿是 0.80，取更低的值把弹道压到躯干中上段、提高命中率；这类怪本就守不住暴击带，所以调低只赚命中。默认 0.65（原 Forge 版胸腔系数 0.76 偏高、常打空）。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;
        y = numberRow(graphics, "巨人瞄点 / Giant Frac", "0.50–1.00", giantAimFracBox, y);
        y = wrapped(graphics,
                "巨人专属瞄准高度系数，默认 0.999（脚上 11.988 / 箱高 12.0，比眼高高 1.548 格）。已与上方 Crits 解耦：改 Crits 不再牵动巨人，也不受 Head Clamp 影响。越接近 1.00 越贴箱顶，暴击容差越小（0.999 时距箱顶仅 0.012 格）。被遮挡时按「距该点最近、下方优先」上下兜底。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        section(graphics, UiText.shown("命中口径", "HITBOX / 命中口径"), y);
        y += 20;
        y = toggleRow(graphics, mouseX, mouseY, "服务端命中箱 / Server Dims", c.serverDims,
                () -> c.serverDims = !c.serverDims, y,
                "用 Hypixel 服务端的判定箱替掉客户端 AABB 的宽高（中心与脚底不动）。默认开启：僵尸 0.9×2.0、骷髅 0.9×2.0、凋灵骷髅 1.0×2.6、狼 1.5×0.7、铁傀儡 1.8×2.7、巨人 3.9×12.0、烈焰人 0.9×2.0、史莱姆 0.51×size；表外的怪保持客户端盒子。数值来自 OceanClient 3.3.8 的 1.8.9 Hypixel 实测——判定箱是服务端属性，版本升级不变。关掉可回到纯客户端 AABB 做对照。 ");
        y = numberRow(graphics, "箱体缩放 / Dims Scale", "0.50–1.50", serverDimsScaleBox, y);
        y = wrapped(graphics,
                "命中箱三围的全局缩放，1.00 = 表值原样。窄了会「看着打中其实没打中」，宽了瞄点会飘出真实判定箱（水平兜底最远滑到箱缘×0.95）；实测微调用。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        y = wrapped(graphics,
                "瞄准点竖扫：从首选高度起每 0.05 格往下找「能打中的最高点」，命中即停；中线被挡时在该层沿视线垂线及左右各 45° 三个方向滑到箱缘（单只目标最多 4 次），下方整段被挡再往上兜底。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;
        y = wrapped(graphics,
                "FOV 是相对当前视线的筛选角度，360° 表示不限制；Max Step 只影响 NORMAL，Brute Step 只在 BRUTE 生效。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        section(graphics, UiText.shown("垂直视角规则", "PITCH POLICY / 垂直视角规则"), y);
        y += 20;
        y = numberRow(graphics, "地平线预留 / Horizon Reserve", "0–8°", pitchHorizonMarginBox, y);
        y = numberRow(graphics, "手动容差 / Hold Tolerance", "0.5–8°", pitchHoldToleranceBox, y);
        y = wrapped(graphics,
                "普通怪：当前 pitch 已落在可用范围内时，Aimbot 不与鼠标争抢；目标高于地平线时会预留指定角度，超出容差才拉回。BadHeadShot 完全按目标点瞄准；巨人自 0.2.68 起与普通怪同一套俯仰策略（已取消「只向上」限制）；贴脸 Zombie（距离不超过 Face-up Dist）会抬头看向天空。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        section(graphics, UiText.shown("拟人化参数", "HUMANIZE / 拟人参数"), y);
        y += 20;
        y = numberRow(graphics, "峰值速度 / Peak Speed", "5–120°/tick", humanPeakBox, y);
        y = numberRow(graphics, "过冲强度 / Overshoot", "0–8", humanOvershootBox, y);
        y = numberRow(graphics, "微摆间隔 / Sweep Interval", "150–4000ms", humanCorrBox, y);
        y = numberRow(graphics, "最大误差 / Max Error", "0–5°", humanMaxErrBox, y);
        y = numberRow(graphics, "重新拉回 / Repull", "2–60°", humanRepullBox, y);
        y = numberRow(graphics, "贴脸距离 / Face-up Dist", "0.2–2 格", faceUpDistBox, y);
        y = numberRow(graphics, "扫射起始回合 / Sweep Min Round", "1–200", sweepMinRoundBox, y);
        y = wrapped(graphics,
                "只有 HUMANIZE 会使用这组参数：峰值速度控制大角度转向上限；过冲模拟超过目标后的回拉；微摆间隔控制锁定附近的非周期漂移；最大误差限制准心偏差；Repull 是偏离过大时重新追踪的阈值；Face-up Dist 是 Zombie 进入抬头逻辑的水平距离。扫射起始回合之前的回合完全关闭锥内扫动与大幅扫描线，只锁单只。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        section(graphics, UiText.shown("暴力扫射", "BRUTE SWEEP / 暴力扫射"), y);
        y += 20;
        y = toggleRow(graphics, mouseX, mouseY, "暴力扫射 / Brute Sweep", c.bruteSweep,
                () -> c.bruteSweep = !c.bruteSweep, y,
                "仅在 BRUTE 模式生效：链式逐个精准锁定并超快速切换——只换到「与当前这只夹角 ≤ 链角」的邻接怪，当前方向没有就近怪就翻向，两个方向都没有就停住锁当前。不是连续扫描线，也不会 360° 乱扫。");
        y = numberRow(graphics, "暴力扫射起始回合 / Brute Min Round", "1–200", bruteSweepMinRoundBox, y);
        y = numberRow(graphics, "扫射链角 / Sweep Chain", "5–180°", bruteSweepChainBox, y);
        y = numberRow(graphics, "每只停留 / Switch Dwell", "40–600ms", bruteSweepDwellBox, y);
        y = numberRow(graphics, "转头完成时间 / Turn Window", "5–50ms", bruteRotationWindowBox, y);
        y = wrapped(graphics,
                "暴力扫射在起始回合之前完全不生效（只按 TOO > 转向角最小 锁单只）；链角决定「下一只能有多远」。"
                        + "【每只停留】就是扫射的本体：每只最多停这么久，到点立刻换下一只——不管有没有打死；"
                        + "目标提前死亡/被挡住也会立刻续链。默认 100ms。"
                        + "【链式推进】（0.2.107，用户定稿 2026-09-17）：从当前这只出发，只换到夹角在链角以内的最近邻接怪，"
                        + "先从左到右（yaw 递增），当前方向没有邻接怪了就翻向从右到左，两个方向都没有就停住锁当前这只，"
                        + "等新怪走进邻接区会自动接着扫。链的断口就是停止点——"
                        + "旧的「锚定锥体 + 锥体过期重锚到准星」已整体删除（那套会越扫越偏，几乎扫遍全图）。"
                        + "场上有巨人不再停掉扫射——按 Clown/Giant 两个优先级开关决定它进不进扫射池。"
                        + "Turn Window 是 BRUTE 把「一次决策的转向量」消耗完所需的时间：控制器只有 20 Hz，"
                        + "这个值越短越接近瞬时转头，默认 25ms 表示半个 tick 内到位，留出更多时间压在目标上；"
                        + "调到 5–10ms 基本等于瞬转。Humanize 的扫射参数与这一组互不影响。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        section(graphics, UiText.shown("手动推偏", "JOYSTICK / 手动推偏"), y);
        y += 20;
        y = toggleRow(graphics, mouseX, mouseY, "手动推偏 / Joystick", c.joystick,
                () -> {
                    c.joystick = !c.joystick;
                    if (c.joystick) c.bruteMode = false;
                }, y, "慢速鼠标移动会累积 yaw/pitch 推偏并参与选目标；快速甩动会暂时交还手动视角。启用时与 BRUTE 互斥。");
        y = numberRow(graphics, "推偏灵敏度 / Sensitivity", "0.2–3", joystickSensitivityBox, y);
        y = numberRow(graphics, "换目标角度 / Switch Deg", "5–60°", joystickSwitchBox, y);
        y = numberRow(graphics, "甩动阈值 / Flick Speed", "300–1500", flickSpeedBox, y);
        y = numberRow(graphics, "甩动退出 / Flick Exit", "50–500ms", flickExitBox, y);
        y = wrapped(graphics,
                "Joystick 是独立输入路径，不套用 HUMANIZE、NORMAL、BRUTE 的 tick 旋转控制器；需要用鼠标主动推偏，甩动超过阈值后会短暂暂停自动接管。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        section(graphics, UiText.shown("HUD 显示与调试", "HUD / 显示与调试"), y);
        y += 20;
        y = toggleRow(graphics, mouseX, mouseY, "显示 Aimbot HUD / Show HUD", c.showHud,
                () -> c.showHud = !c.showHud, y, "显示目标名、当前模式、锁定状态和攻击状态；位置可在 HUD Layout 中拖动调整。");
        y = toggleRow(graphics, mouseX, mouseY, "显示快捷键 / Show Key Hints", c.showKeyHints,
                () -> c.showKeyHints = !c.showKeyHints, y, "在 HUD 第二行显示 TOO/GOL/SLM、CLO/GIA、BAB、CLS 等分组快捷键提示。");
        y = toggleRow(graphics, mouseX, mouseY, "游戏结束自动隐藏 / Hide On Game Over", c.hudHideOnGameOver,
                () -> c.hudHideOnGameOver = !c.hudHideOnGameOver, y,
                "整局结束时（出现 Zombies - 时间 / SURVIVED 结算那一行，赢输都算）立刻隐藏 Aimbot HUD，10 秒后自动恢复；窗口内分组快捷键不响应，也不会发任何聊天提示。");
        y = toggleRow(graphics, mouseX, mouseY, "调试视线 / Debug Line", c.debugLine,
                () -> c.debugLine = !c.debugLine, y, "从玩家视线起点渲染到最终攻击点的绿线，用于检查目标点、AimLead 和穿透路径。");
        y = wrapped(graphics, UiText.shown("Aimbot HUD 只负责显示，不影响瞄准逻辑。想确认瞄的点对不对时可以临时打开 Debug Line，看完记得关掉。", "Aimbot HUD 是独立显示模块，不改变瞄准逻辑；需要检查目标点时可临时打开 Debug Line，确认后建议关闭。"),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        setContentHeight(y - (contentTop() - scrollOffset()));
    }

    private int toggleRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                          String label, boolean value, Runnable action, int y, String desc) {
        graphics.text(font, label, contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, value,
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        toggleHits.add(new ToggleHit(contentRight() - 44, y + 1, action));
        return wrapped(graphics, desc, contentLeft(), y + 21, TEXT_DIM, contentWidth()) + 6;
    }

    private int keyRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, String label,
                       Supplier<int[]> getter, Consumer<int[]> setter, int y, String desc) {
        graphics.text(font, label, contentLeft(), y + 4, TEXT);
        int x = contentRight() - 174;
        int w = 174;
        boolean listening = keyHits.size() == listeningIndex;
        String text = listening ? "按键 " + capturedCount + "/" + KeyChord.MAX_KEYS + " · ESC完成"
                : KeyChord.display(getter.get());
        drawButton(graphics, text, x, y, w, 18, isInside(mouseX, mouseY, x, y, w, 18));
        keyHits.add(new KeyHit(x, y, w, 18, getter, setter));
        return wrapped(graphics, desc, contentLeft(), y + 21, TEXT_DIM, contentWidth()) + 6;
    }

    private int modeRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, String label,
                        String value, Runnable action, int y, String desc) {
        graphics.text(font, label, contentLeft(), y + 4, TEXT);
        int x = contentRight() - 174;
        int w = 174;
        drawButton(graphics, value, x, y, w, 18,
                isInside(mouseX, mouseY, x, y, w, 18));
        modeHits.add(new ModeHit(x, y, w, 18, action));
        return wrapped(graphics, desc, contentLeft(), y + 21, TEXT_DIM, contentWidth()) + 6;
    }

    private static String aimStyle(AimbotConfig c) {
        if (c.bruteMode) return "BRUTE / 暴力";
        return c.humanize ? "HUMANIZE / 拟人" : "NORMAL / 普通";
    }

    private static void cycleAimStyle(AimbotConfig c) {
        if (c.bruteMode) {
            c.bruteMode = false;
            c.humanize = true;
        } else if (c.humanize) {
            c.humanize = false;
        } else {
            c.bruteMode = true;
            c.humanize = false;
            c.joystick = false;
        }
    }

    private int numberRow(GuiGraphicsExtractor graphics, String label, String hint,
                          EditBox box, int y) {
        graphics.text(font, label, contentLeft(), y + 4, TEXT);
        graphics.text(font, hint, contentRight() - 92 - font.width(hint), y + 6, TEXT_FAINT);
        box.setX(contentRight() - 84);
        box.setY(y);
        return y + 30;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listeningIndex >= 0) {
            if (event.button() != 0) {
                capture(-100 + event.button());
            }
            return true;
        }
        if (event.button() == 0) {
            for (ToggleHit hit : toggleHits) {
                if (isInside(event.x(), event.y(), hit.x, hit.y, 44, 16)) {
                    hit.action.run();
                    return true;
                }
            }
            for (ModeHit hit : modeHits) {
                if (isInside(event.x(), event.y(), hit.x, hit.y, hit.width, hit.height)) {
                    hit.action.run();
                    return true;
                }
            }
            for (int i = 0; i < keyHits.size(); i++) {
                KeyHit hit = keyHits.get(i);
                if (!isInside(event.x(), event.y(), hit.x, hit.y, hit.width, hit.height)) continue;
                listeningIndex = i;
                capturedKeys = new int[KeyChord.MAX_KEYS];
                capturedCount = 0;
                return true;
            }
        } else if (event.button() == 1) {
            for (KeyHit hit : keyHits) {
                if (isInside(event.x(), event.y(), hit.x, hit.y, hit.width, hit.height)) {
                    hit.setter.accept(KeyChord.EMPTY);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningIndex >= 0) {
            if (event.isEscape()) {
                if (capturedCount > 0) finishCapture();
                else listeningIndex = -1;
            } else {
                capture(event.key());
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private void capture(int code) {
        if (code == 0 || capturedCount >= KeyChord.MAX_KEYS) return;
        for (int i = 0; i < capturedCount; i++) if (capturedKeys[i] == code) return;
        capturedKeys[capturedCount++] = code;
        if (capturedCount >= KeyChord.MAX_KEYS) finishCapture();
    }

    private void finishCapture() {
        if (listeningIndex < 0 || listeningIndex >= keyHits.size()) return;
        KeyHit hit = keyHits.get(listeningIndex);
        hit.setter.accept(capturedKeys);
        listeningIndex = -1;
        capturedKeys = new int[KeyChord.MAX_KEYS];
        capturedCount = 0;
    }

    @Override
    protected void saveAndClose() {
        AimbotConfig c = module.config();
        try {
            c.threatDist = parseInt(threatDistBox, 3, 10, "Threat Dist");
            c.fov = parseInt(fovBox, 30, 360, "FOV");
            c.maxDegPerTick = parseInt(maxStepBox, 5, 90, "Max Step");
            c.bruteMaxDegPerTick = parseInt(bruteMaxStepBox, 30, 180, "Brute Step");
            c.maxDist = parseInt(maxDistBox, 10, 400, "Max Dist");
            c.crits = parseDouble(critsBox, -0.2, 0.5, "Crits");
            c.vcrits = parseDouble(vcritsBox, 0.0, 1.0, "VCrits");
            c.headFracMax = parseDouble(headFracMaxBox, 0.01, 2.0, "Head Clamp");
            c.badHeadshotFrac = parseDouble(badHeadshotFracBox, 0.30, 0.95, "BadHS Frac");
            c.giantAimFrac = parseDouble(giantAimFracBox, 0.50, 1.00, "Giant Frac");
            c.serverDimsScale = parseDouble(serverDimsScaleBox, 0.50, 1.50, "Dims Scale");
            c.immortalLrWindowSec = parseInt(immortalLrWindowBox, 5, 30, "LR Window");
            c.joystickSensitivity = parseDouble(joystickSensitivityBox, 0.2, 3.0, "Sensitivity");
            c.joystickSwitchDeg = parseInt(joystickSwitchBox, 5, 60, "Switch Deg");
            c.joystickFlickPxPerSec = parseInt(flickSpeedBox, 300, 1500, "Flick Speed");
            c.joystickFlickExitMs = parseInt(flickExitBox, 50, 500, "Flick Exit");
            c.humanizePeakDeg = parseInt(humanPeakBox, 5, 120, "Peak Speed");
            c.humanizeOvershoot = parseDouble(humanOvershootBox, 0.0, 8.0, "Overshoot");
            c.humanizeCorrMs = parseInt(humanCorrBox, 150, 4000, "Sweep Interval");
            c.humanizeMaxErrDeg = parseDouble(humanMaxErrBox, 0.0, 5.0, "Max Error");
            c.humanizeRepullDeg = parseDouble(humanRepullBox, 2.0, 60.0, "Repull");
            c.faceUpDist = parseDouble(faceUpDistBox, 0.2, 2.0, "Face-up Dist");
            c.pitchHorizonMarginDeg = parseDouble(pitchHorizonMarginBox, 0.0, 8.0, "Horizon Reserve");
            c.pitchHoldToleranceDeg = parseDouble(pitchHoldToleranceBox, 0.5, 8.0, "Hold Tolerance");
            c.midFallSpeed = parseDouble(midFallSpeedBox, 0.5, 4.0, "Mid Fall Speed");
            c.aboveHeightBlocks = parseDouble(aboveHeightBox, 1.0, 32.0, "Above Height");
            c.sweepMinRound = parseInt(sweepMinRoundBox, 1, 200, "Sweep Min Round");
            c.bruteSweepMinRound = parseInt(bruteSweepMinRoundBox, 1, 200, "Brute Sweep Min Round");
            c.bruteSweepChainDeg = parseDouble(bruteSweepChainBox, 5.0, 180.0, "Brute Sweep Chain");
            c.bruteSweepDwellMs = parseInt(bruteSweepDwellBox, 0, 600, "Brute Sweep Dwell");
            c.bruteRotationWindowMs = parseInt(bruteRotationWindowBox, 5, 50, "Brute Turn Window");
            c.save();
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            // parse helpers put the field name in the exception message.
            setErrorMessage(exception.getMessage() == null ? "数值格式不正确" : exception.getMessage());
        }
    }

    private int parseInt(EditBox box, int min, int max, String label) {
        int value;
        try {
            value = Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException exception) {
            throw new NumberFormatException(label + " 必须是数字");
        }
        if (value < min || value > max) throw new NumberFormatException(label + " 超出范围 " + min + "–" + max);
        return value;
    }

    private double parseDouble(EditBox box, double min, double max, String label) {
        double value;
        try {
            value = Double.parseDouble(box.getValue().trim());
        } catch (NumberFormatException exception) {
            throw new NumberFormatException(label + " 必须是数字");
        }
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new NumberFormatException(label + " 超出范围 " + min + "–" + max);
        }
        return value;
    }

    private static final class ToggleHit {
        final int x;
        final int y;
        final Runnable action;

        ToggleHit(int x, int y, Runnable action) {
            this.x = x;
            this.y = y;
            this.action = action;
        }
    }

    private static final class ModeHit {
        final int x;
        final int y;
        final int width;
        final int height;
        final Runnable action;

        ModeHit(int x, int y, int width, int height, Runnable action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.action = action;
        }
    }

    private static final class KeyHit {
        final int x;
        final int y;
        final int width;
        final int height;
        final Supplier<int[]> getter;
        final Consumer<int[]> setter;

        KeyHit(int x, int y, int width, int height, Supplier<int[]> getter, Consumer<int[]> setter) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.getter = getter;
            this.setter = setter;
        }
    }
}

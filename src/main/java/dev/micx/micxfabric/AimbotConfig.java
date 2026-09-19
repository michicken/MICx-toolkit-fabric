package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Fabric properties/config compatibility layer for the Forge 1.8.9 Aimbot settings. */
public final class AimbotConfig {
    public boolean onlyFire = true;
    public boolean zombiesOnly = true;
    public boolean aimLead = true;
    public boolean wsStair = true;
    public int threatDist = 6;
    public boolean threatEnabled = true;
    public boolean nearestFirst = false;
    public boolean joystick = false;
    public double joystickSensitivity = 1.0;
    public int joystickSwitchDeg = 15;
    public int joystickFlickPxPerSec = 700;
    public int joystickFlickExitMs = 200;
    public int aimbotHudDx = 0;
    public int aimbotHudDy = 0;
    public boolean debugLine = false;
    public int maxDegPerTick = 30;
    public int maxDist = 200;

    /** Humanized rotation defaults ported from the final 1.8.9 Aimbot path. */
    public boolean humanize = true;
    /** Explicit aggressive rotation/selection style; kept separate from legacy humanize. */
    public boolean bruteMode = false;
    public int bruteMaxDegPerTick = 180;
    public int humanizePeakDeg = 30;
    public double humanizeOvershoot = 5.0;
    public int humanizeCorrMs = 450;
    public double humanizeMaxErrDeg = 1.0;
    public double humanizeRepullDeg = 10.0;
    public double faceUpDist = 0.5;
    /**
     * Humanize 扫射（锥内扫动 + 大幅扫描线）的起始回合：回合数小于该值时完全关闭扫射，
     * 只锁单只。非 Zombies 局或回合未知时保持原有行为（不关闭）。
     */
    public int sweepMinRound = 49;

    /**
     * BRUTE（暴力）模式专用扫射：**链式**逐个精准锁定 + 超快速切换，快速扫过一堆怪里的
     * 每一个目标（与 Humanize 的连续扫描线不同）；只换到链角范围内的邻接怪。
     */
    public boolean bruteSweep = true;
    /** BRUTE 扫射的起始回合（用户定稿 36）；回合未知时不门控。 */
    public int bruteSweepMinRound = 36;
    /**
     * BRUTE 扫射的**链角**（°，用户定稿 2026-09-17）：只换到「与当前这只夹角 ≤ 链角」的
     * 邻接怪；当前方向没有邻接怪就翻向（从左到右 ↔ 从右到左），两个方向都没有就停住锁当前。
     * 链的断口 = 停止点，不再有锚定锥体/重锚那套「扫着扫着扫遍全图」。
     * （旧名 bruteSweepFovDeg / "扫射 FOV 半角"，语义由「窗口半角」改为「邻接夹角上限」。）
     */
    public double bruteSweepChainDeg = 45.0;
    /**
     * BRUTE 扫射在每个目标上的停留时间（ms）——<b>扫射的本体就是这个</b>：
     * 每只最多停这么久，到点立刻换下一只，<b>不管有没有打死</b>；目标提前死亡/掉出锥体
     * 也会立刻换。0.2.69 引入时默认 120ms；0.2.90 曾被改成「0 = 只在死亡时推进」，
     * 结果扫射退化成锁单只（2026-09-15 用户复现并定位），0.2.100 起把默认恢复成 100ms
     * 并把下限抬到 40ms —— 「不换目标」不再是一个可选项，它只会让扫射不工作。
     */
    public int bruteSweepDwellMs = 100;
    /**
     * BRUTE 旋转的渲染消耗窗口（ms）。控制器每 20 Hz 决策一次并给出「一整个 tick」的
     * 转向量，渲染层在这个窗口内把它消耗完。窗口越短转速越快：默认 25ms 表示一次
     * 决策的转向量在半个 tick 内完成，目标上的锚定时间更长，也更不容易在切换时打空。
     */
    public int bruteRotationWindowMs = 25;

    /** Vertical lazy-lock policy for ordinary mobs. */
    public double pitchHorizonMarginDeg = 3.0;
    public double pitchHoldToleranceDeg = 2.0;

    public boolean ignoreToo = false;
    public boolean ignoreGolem = false;
    public boolean ignoreSlime = false;
    public boolean ignoreVerticalFall = true;
    /**
     * 忽略 mid（AA 花坛 / 飞碟四口正下方）内高速自由落体的怪。UFO 从 y≈105 放下的怪
     * 在落地前不参与选靶；被打飞（水平速度大）与窗户下落（贴地且不在 mid）不受影响。
     */
    public boolean ignoreMidFall = true;
    /** 判定"高速下落"的垂直速度阈值（格/tick），越大越严格。 */
    public double midFallSpeed = 1.8;
    /**
    /**
     * 忽略玩家上方高度差过大的目标（高台上够不着的怪）。
     * 判据：怪脚底 y − 玩家脚底 y > 阈值。恶魂（Ghast）是飞行怪，豁免本条。
     * 第 21 回合整条豁免（飞碟/高处投放怪密集），阈值不变。
     */
    public boolean ignoreAbovePlayer = true;
    /** 高度差阈值（格）。超过这个高度差的目标不参与选靶（恶魂除外、R21 豁免）。 */
    public double aboveHeightBlocks = 5.0;
    /**
     * 无敌怪判定的 LR 门控（用户定稿 2026-09-16）：只有 LR 会造成无敌怪，所以只有最近
     * {@link #immortalLrWindowSec} 秒内释放过 LR 才做被动判定。关掉=恢复旧行为（一直可判定）。
     */
    public boolean immortalLrGate = true;
    /** LR 门控窗口（秒），默认 15。窗口外的「无真实伤害」时长不计入判定证据。 */
    public int immortalLrWindowSec = 15;

    /** LR 门控窗口（毫秒）。 */
    public long immortalLrWindowMs() { return immortalLrWindowSec * 1000L; }
    /** Clown 模式：小丑进首选档，同时把巨人压到末位档（先清小丑小怪，只剩巨人才锁它）。与下一项互斥。 */
    public boolean prioClown = false;
    /** Giant 模式：巨人进首选档，有巨人就先锁巨人。与上一项互斥。 */
    public boolean prioGiant = false;
    public int fov = 360;

    public boolean showHud = true;
    public boolean showKeyHints = true;
    /** 整局游戏结束时立刻隐藏 Aimbot HUD，{@link AimbotRules#GAME_OVER_HUD_HIDE_MS} 毫秒后自动恢复（不发聊天提示）。 */
    public boolean hudHideOnGameOver = true;
    public boolean closest = false;
    public boolean holdLock = false;

    /** Bridger 1.8.9 head-point controls retained in the unified Fabric config. */
    public boolean insta = false;
    /**
     * 自动 insta 窗口：聊天栏出现 Insta Kill 激活事件时自动切到 insta 瞄点，
     * 道具计时结束后自动退出。默认开启（对齐 Forge 原版 autoInsta 的默认值）。
     */
    public boolean autoInsta = true;
    public double crits = 0.0;
    public double vcrits = 0.0;
    public double headFracMax = 0.98;

    /**
     * BadHeadShot 怪的瞄准高度系数（作用在幽灵框上）。
     * 0.80 是爆头带下沿，取更低的值把弹道压到躯干中上段、提高命中率。
     * 默认值来源见 {@link AimbotRules#BAD_HEADSHOT_BODY_FRAC_DEFAULT}。
     */
    public double badHeadshotFrac = AimbotRules.BAD_HEADSHOT_BODY_FRAC_DEFAULT;

    /**
     * 巨人的瞄准高度系数（作用在幽灵框上），默认 0.999（脚上 11.988 / 箱高 12.0）。
     * 与全局 {@link #crits} 解耦，且不受 {@link #headFracMax} 夹取——
     * 本字段自身的 [0.50, 1.00] 范围已保证瞄点不会超出箱体。
     */
    public double giantAimFrac = AimbotRules.GIANT_AIM_FRAC_DEFAULT;

    /**
     * 用服务端口径的命中箱（{@link AimTargetDims}）替掉客户端 AABB 的宽高。
     * Hypixel 的判定箱比客户端渲染盒宽，照客户端盒子算瞄点会「看着打在头上、服务端判没中」。
     */
    public boolean serverDims = true;
    /** 命中箱三围的全局缩放，1.0 = 表值原样；供实测微调（面板 0.50–1.50）。 */
    public double serverDimsScale = 1.0;

    /**
     * 窗优先模式（仅 AA 局生效）：0=关 1=P2+P3+P4(+P4傀儡) 2=P5 3=P1+ULT(+ULT傀儡) 4=ALT。
     * 五档选靶：窗怪 &gt; 窗傀儡 &gt; 普通 &gt; baby/降级 &gt; 类型忽略（2026-09-19 定稿）。
     */
    public int windowPriorityMode = 0;

    private boolean loaded;
    private int[] ignoreTooKey = KeyChord.EMPTY;
    private int[] ignoreGolemKey = KeyChord.EMPTY;
    private int[] ignoreSlimeKey = KeyChord.EMPTY;
    private int[] prioClownKey = KeyChord.EMPTY;
    private int[] prioGiantKey = KeyChord.EMPTY;
    private int[] closestKey = KeyChord.EMPTY;
    private int[] holdLockKey = KeyChord.EMPTY;
    private int[] toggleKey = KeyChord.EMPTY;
    /** SR 模式（窗优先）循环切换键；按一下切到下一模式，HUD 实时显示简写。 */
    private int[] srModeKey = KeyChord.EMPTY;

    public void load() {
        if (loaded) return;
        loaded = true;
        Path current = FabricRuntime.configPath().resolve("aimbot.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_Aimbot.cfg");
        Properties p = ConfigProperties.load(current, legacy);

        onlyFire = ConfigProperties.bool(p, "onlyFire", true);
        zombiesOnly = ConfigProperties.bool(p, "zombiesOnly", true);
        aimLead = ConfigProperties.bool(p, "aimLead", true);
        wsStair = ConfigProperties.bool(p, "wsStair", true);
        threatDist = ConfigProperties.integer(p, "threatDist", 6, 3, 10);
        threatEnabled = ConfigProperties.bool(p, "threatEnabled", true);
        nearestFirst = ConfigProperties.bool(p, "nearestFirst", false);
        joystick = ConfigProperties.bool(p, "joystick", false);
        joystickSensitivity = ConfigProperties.real(p, "joystickSensitivity", 1.0, 0.2, 3.0);
        joystickSwitchDeg = ConfigProperties.integer(p, "joystickSwitchDeg", 15, 5, 60);
        joystickFlickPxPerSec = ConfigProperties.integer(p, "joystickFlickPxPerSec", 700, 300, 1500);
        joystickFlickExitMs = ConfigProperties.integer(p, "joystickFlickExitMs", 200, 50, 500);
        aimbotHudDx = ConfigProperties.integer(p, "aimbotHudDx", 0, -2000, 2000);
        aimbotHudDy = ConfigProperties.integer(p, "aimbotHudDy", 0, -2000, 2000);
        debugLine = ConfigProperties.bool(p, "debugLine", false);
        maxDegPerTick = ConfigProperties.integer(p, "maxDegPerTick", 30, 5, 90);
        maxDist = ConfigProperties.integer(p, "maxDist", 200, 10, 400);

        humanize = ConfigProperties.bool(p, "humanize", true);
        bruteMode = ConfigProperties.bool(p, "bruteMode", false);
        bruteMaxDegPerTick = ConfigProperties.integer(p, "bruteMaxDegPerTick", 180, 30, 180);
        if (bruteMode) joystick = false;
        humanizePeakDeg = ConfigProperties.integer(p, "humanizePeakDeg", 30, 5, 120);
        humanizeOvershoot = ConfigProperties.real(p, "humanizeOvershoot", 5.0, 0.0, 8.0);
        humanizeCorrMs = ConfigProperties.integer(p, "humanizeCorrMs", 450, 150, 4000);
        humanizeMaxErrDeg = ConfigProperties.real(p, "humanizeMaxErrDeg", 1.0, 0.0, 5.0);
        humanizeRepullDeg = ConfigProperties.real(p, "humanizeRepullDeg", 10.0, 2.0, 60.0);
        faceUpDist = ConfigProperties.real(p, "faceUpDist", 0.5, 0.2, 2.0);
        sweepMinRound = ConfigProperties.integer(p, "sweepMinRound", 49, 1, 200);
        bruteSweep = ConfigProperties.bool(p, "bruteSweep", true);
        bruteSweepMinRound = ConfigProperties.integer(p, "bruteSweepMinRound", 36, 1, 200);
        bruteSweepChainDeg = ConfigProperties.real(p, "bruteSweepChainDeg", 45.0, 5.0, 180.0);
        bruteSweepDwellMs = ConfigProperties.integer(p, "bruteSweepDwellMs", 100, 40, 600);
        bruteRotationWindowMs = ConfigProperties.integer(p, "bruteRotationWindowMs", 25, 5, 50);
        pitchHorizonMarginDeg = ConfigProperties.real(p, "pitchHorizonMarginDeg", 3.0, 0.0, 8.0);
        pitchHoldToleranceDeg = ConfigProperties.real(p, "pitchHoldToleranceDeg", 2.0, 0.5, 8.0);

        ignoreToo = ConfigProperties.bool(p, "ignoreToo", false);
        ignoreGolem = ConfigProperties.bool(p, "ignoreGolem", false);
        ignoreSlime = ConfigProperties.bool(p, "ignoreSlime", false);
        ignoreVerticalFall = ConfigProperties.bool(p, "ignoreVerticalFall", true);
        ignoreMidFall = ConfigProperties.bool(p, "ignoreMidFall", true);
        midFallSpeed = ConfigProperties.real(p, "midFallSpeed", 1.8, 0.5, 4.0);
        ignoreAbovePlayer = ConfigProperties.bool(p, "ignoreAbovePlayer", true);
        aboveHeightBlocks = ConfigProperties.real(p, "aboveHeightBlocks", 5.0, 1.0, 32.0);
        prioClown = ConfigProperties.bool(p, "prioClown", false);
        prioGiant = ConfigProperties.bool(p, "prioGiant", false);
        if (prioClown && prioGiant) prioGiant = false;
        fov = ConfigProperties.integer(p, "fov", 360, 30, 360);
        showHud = ConfigProperties.bool(p, "showHud", true);
        showKeyHints = ConfigProperties.bool(p, "showKeyHints", true);
        hudHideOnGameOver = ConfigProperties.bool(p, "hudHideOnGameOver", true);
        immortalLrGate = ConfigProperties.bool(p, "immortalLrGate", true);
        immortalLrWindowSec = ConfigProperties.integer(p, "immortalLrWindowSec", 15, 5, 30);
        closest = ConfigProperties.bool(p, "closest", false);
        holdLock = ConfigProperties.bool(p, "holdLock", false);

        insta = ConfigProperties.bool(p, "insta", false);
        autoInsta = ConfigProperties.bool(p, "autoInsta", true);
        crits = ConfigProperties.real(p, "crits", 0.0, -0.2, 0.5);
        vcrits = ConfigProperties.real(p, "vcrits", 0.0, 0.0, 1.0);
        headFracMax = ConfigProperties.real(p, "headFracMax", 0.98, 0.01, 2.0);
        badHeadshotFrac = ConfigProperties.real(p, "badHeadshotFrac",
                AimbotRules.BAD_HEADSHOT_BODY_FRAC_DEFAULT, 0.30, 0.95);
        giantAimFrac = ConfigProperties.real(p, "giantAimFrac",
                AimbotRules.GIANT_AIM_FRAC_DEFAULT, 0.50, 1.00);
        serverDims = ConfigProperties.bool(p, "serverDims", true);
        serverDimsScale = ConfigProperties.real(p, "serverDimsScale", 1.0, 0.50, 1.50);
        windowPriorityMode = ConfigProperties.integer(p, "windowPriorityMode", 0,
                AimbotRules.WP_OFF, AimbotRules.WP_ALT);

        ignoreTooKey = readKey(p, "ignoreTooKeyCodes", "ignoreTooKeyCode");
        ignoreGolemKey = readKey(p, "ignoreGolemKeyCodes", "ignoreGolemKeyCode");
        ignoreSlimeKey = readKey(p, "ignoreSlimeKeyCodes", "ignoreSlimeKeyCode");
        prioClownKey = readKey(p, "prioClownKeyCodes", "prioClownKeyCode");
        prioGiantKey = readKey(p, "prioGiantKeyCodes", "prioGiantKeyCode");
        closestKey = readKey(p, "closestKeyCodes", "closestKeyCode");
        holdLockKey = readKey(p, "holdLockKeyCodes", "holdLockKeyCode");
        toggleKey = readKey(p, "toggleKeyCodes", "toggleKeyCode");
        srModeKey = readKey(p, "srModeKeyCodes", "srModeKeyCode");
    }

    public void save() {
        load();
        if (bruteMode) joystick = false;
        Properties p = new Properties();
        put(p, "onlyFire", onlyFire);
        put(p, "zombiesOnly", zombiesOnly);
        put(p, "aimLead", aimLead);
        put(p, "wsStair", wsStair);
        put(p, "threatDist", clamp(threatDist, 3, 10));
        put(p, "threatEnabled", threatEnabled);
        put(p, "nearestFirst", nearestFirst);
        put(p, "joystick", joystick);
        put(p, "joystickSensitivity", clamp(joystickSensitivity, 0.2, 3.0));
        put(p, "joystickSwitchDeg", clamp(joystickSwitchDeg, 5, 60));
        put(p, "joystickFlickPxPerSec", clamp(joystickFlickPxPerSec, 300, 1500));
        put(p, "joystickFlickExitMs", clamp(joystickFlickExitMs, 50, 500));
        put(p, "aimbotHudDx", clamp(aimbotHudDx, -2000, 2000));
        put(p, "aimbotHudDy", clamp(aimbotHudDy, -2000, 2000));
        put(p, "debugLine", debugLine);
        put(p, "maxDegPerTick", clamp(maxDegPerTick, 5, 90));
        put(p, "maxDist", clamp(maxDist, 10, 400));

        put(p, "humanize", humanize);
        put(p, "bruteMode", bruteMode);
        put(p, "bruteMaxDegPerTick", clamp(bruteMaxDegPerTick, 30, 180));
        put(p, "humanizePeakDeg", clamp(humanizePeakDeg, 5, 120));
        put(p, "humanizeOvershoot", clamp(humanizeOvershoot, 0.0, 8.0));
        put(p, "humanizeCorrMs", clamp(humanizeCorrMs, 150, 4000));
        put(p, "humanizeMaxErrDeg", clamp(humanizeMaxErrDeg, 0.0, 5.0));
        put(p, "humanizeRepullDeg", clamp(humanizeRepullDeg, 2.0, 60.0));
        put(p, "faceUpDist", clamp(faceUpDist, 0.2, 2.0));
        put(p, "pitchHorizonMarginDeg", clamp(pitchHorizonMarginDeg, 0.0, 8.0));
        put(p, "pitchHoldToleranceDeg", clamp(pitchHoldToleranceDeg, 0.5, 8.0));
        put(p, "ignoreToo", ignoreToo);
        put(p, "ignoreGolem", ignoreGolem);
        put(p, "ignoreSlime", ignoreSlime);
        put(p, "ignoreVerticalFall", ignoreVerticalFall);
        put(p, "ignoreMidFall", ignoreMidFall);
        put(p, "midFallSpeed", clamp(midFallSpeed, 0.5, 4.0));
        put(p, "ignoreAbovePlayer", ignoreAbovePlayer);
        put(p, "aboveHeightBlocks", clamp(aboveHeightBlocks, 1.0, 32.0));
        put(p, "sweepMinRound", clamp(sweepMinRound, 1, 200));
        put(p, "bruteSweep", bruteSweep);
        put(p, "bruteSweepMinRound", clamp(bruteSweepMinRound, 1, 200));
        put(p, "bruteSweepChainDeg", clamp(bruteSweepChainDeg, 5.0, 180.0));
        put(p, "bruteSweepDwellMs", clamp(bruteSweepDwellMs, 40, 600));
        put(p, "bruteRotationWindowMs", clamp(bruteRotationWindowMs, 5, 50));
        put(p, "prioClown", prioClown);
        put(p, "prioGiant", prioGiant);
        put(p, "fov", clamp(fov, 30, 360));
        put(p, "showHud", showHud);
        put(p, "showKeyHints", showKeyHints);
        put(p, "hudHideOnGameOver", hudHideOnGameOver);
        put(p, "immortalLrGate", immortalLrGate);
        put(p, "immortalLrWindowSec", clamp(immortalLrWindowSec, 5, 30));
        put(p, "closest", closest);
        put(p, "holdLock", holdLock);
        put(p, "insta", insta);
        put(p, "autoInsta", autoInsta);
        put(p, "crits", clamp(crits, -0.2, 0.5));
        put(p, "vcrits", clamp(vcrits, 0.0, 1.0));
        put(p, "headFracMax", clamp(headFracMax, 0.01, 2.0));
        put(p, "badHeadshotFrac", clamp(badHeadshotFrac, 0.30, 0.95));
        put(p, "giantAimFrac", clamp(giantAimFrac, 0.50, 1.00));
        put(p, "serverDims", serverDims);
        put(p, "serverDimsScale", clamp(serverDimsScale, 0.50, 1.50));
        put(p, "windowPriorityMode", clamp(windowPriorityMode,
                AimbotRules.WP_OFF, AimbotRules.WP_ALT));

        writeKey(p, "ignoreTooKeyCodes", "ignoreTooKeyCode", ignoreTooKey);
        writeKey(p, "ignoreGolemKeyCodes", "ignoreGolemKeyCode", ignoreGolemKey);
        writeKey(p, "ignoreSlimeKeyCodes", "ignoreSlimeKeyCode", ignoreSlimeKey);
        writeKey(p, "prioClownKeyCodes", "prioClownKeyCode", prioClownKey);
        writeKey(p, "prioGiantKeyCodes", "prioGiantKeyCode", prioGiantKey);
        writeKey(p, "closestKeyCodes", "closestKeyCode", closestKey);
        writeKey(p, "holdLockKeyCodes", "holdLockKeyCode", holdLockKey);
        writeKey(p, "toggleKeyCodes", "toggleKeyCode", toggleKey);
        writeKey(p, "srModeKeyCodes", "srModeKeyCode", srModeKey);
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("aimbot.properties"), p,
                    "MICx Aimbot configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save Aimbot configuration", exception);
        }
    }

    public void setPrioClown(boolean value) {
        prioClown = value;
        if (value) prioGiant = false;
    }

    public void setPrioGiant(boolean value) {
        prioGiant = value;
        if (value) prioClown = false;
    }

    public int[] getIgnoreTooKey() { load(); return ignoreTooKey.clone(); }
    public int[] getIgnoreGolemKey() { load(); return ignoreGolemKey.clone(); }
    public int[] getIgnoreSlimeKey() { load(); return ignoreSlimeKey.clone(); }
    public int[] getPrioClownKey() { load(); return prioClownKey.clone(); }
    public int[] getPrioGiantKey() { load(); return prioGiantKey.clone(); }
    public int[] getClosestKey() { load(); return closestKey.clone(); }
    public int[] getHoldLockKeyCodes() { load(); return holdLockKey.clone(); }
    public int[] getToggleKeyCodes() { load(); return toggleKey.clone(); }
    public int[] getSrModeKey() { load(); return srModeKey.clone(); }

    public void setIgnoreTooKey(int[] value) { ignoreTooKey = setKey(value); save(); }
    public void setIgnoreGolemKey(int[] value) { ignoreGolemKey = setKey(value); save(); }
    public void setIgnoreSlimeKey(int[] value) { ignoreSlimeKey = setKey(value); save(); }
    public void setPrioClownKey(int[] value) { prioClownKey = setKey(value); save(); }
    public void setPrioGiantKey(int[] value) { prioGiantKey = setKey(value); save(); }
    public void setClosestKey(int[] value) { closestKey = setKey(value); save(); }
    public void setHoldLockKeyCodes(int[] value) { holdLockKey = setKey(value); save(); }
    public void setToggleKeyCodes(int[] value) { toggleKey = setKey(value); save(); }
    public void setSrModeKey(int[] value) { srModeKey = setKey(value); save(); }

    private int[] setKey(int[] value) {
        return KeyChord.normalize(value);
    }

    private static int[] readKey(Properties p, String newKey, String oldKey) {
        return KeyChord.readConfig(p, newKey, oldKey, 0);
    }

    private static void writeKey(Properties p, String newKey, String oldKey, int[] value) {
        KeyChord.writeConfig(p, newKey, oldKey, value);
    }

    private static void put(Properties p, String key, boolean value) {
        p.setProperty(key, Boolean.toString(value));
    }

    private static void put(Properties p, String key, int value) {
        p.setProperty(key, Integer.toString(value));
    }

    private static void put(Properties p, String key, double value) {
        p.setProperty(key, Double.toString(value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

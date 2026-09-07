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
     * 忽略玩家上方高度差过大的目标（高台上够不着的怪）。
     * 判据：怪脚底 y − 玩家脚底 y > 阈值。恶魂（Ghast）是飞行怪，豁免本条。
     */
    public boolean ignoreAbovePlayer = true;
    /** 高度差阈值（格）。超过这个高度差的目标不参与选靶（恶魂除外）。 */
    public double aboveHeightBlocks = 5.0;
    public boolean prioClown = false;
    public boolean prioGiant = false;
    public boolean prioBaby = false;
    public int fov = 360;

    public boolean showHud = true;
    public boolean showKeyHints = true;
    public boolean closest = false;
    public boolean holdLock = false;

    /** Bridger 1.8.9 head-point controls retained in the unified Fabric config. */
    public boolean insta = false;
    public double crits = 0.0;
    public double vcrits = 0.0;
    public double headFracMax = 0.98;

    private boolean loaded;
    private int[] ignoreTooKey = KeyChord.EMPTY;
    private int[] ignoreGolemKey = KeyChord.EMPTY;
    private int[] ignoreSlimeKey = KeyChord.EMPTY;
    private int[] prioClownKey = KeyChord.EMPTY;
    private int[] prioGiantKey = KeyChord.EMPTY;
    private int[] prioBabyKey = KeyChord.EMPTY;
    private int[] closestKey = KeyChord.EMPTY;
    private int[] holdLockKey = KeyChord.EMPTY;
    private int[] toggleKey = KeyChord.EMPTY;

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
        prioBaby = ConfigProperties.bool(p, "prioBaby", false);
        fov = ConfigProperties.integer(p, "fov", 360, 30, 360);
        showHud = ConfigProperties.bool(p, "showHud", true);
        showKeyHints = ConfigProperties.bool(p, "showKeyHints", true);
        closest = ConfigProperties.bool(p, "closest", false);
        holdLock = ConfigProperties.bool(p, "holdLock", false);

        insta = ConfigProperties.bool(p, "insta", false);
        crits = ConfigProperties.real(p, "crits", 0.0, -0.2, 0.5);
        vcrits = ConfigProperties.real(p, "vcrits", 0.0, 0.0, 1.0);
        headFracMax = ConfigProperties.real(p, "headFracMax", 0.98, 0.01, 2.0);

        ignoreTooKey = readKey(p, "ignoreTooKeyCodes", "ignoreTooKeyCode");
        ignoreGolemKey = readKey(p, "ignoreGolemKeyCodes", "ignoreGolemKeyCode");
        ignoreSlimeKey = readKey(p, "ignoreSlimeKeyCodes", "ignoreSlimeKeyCode");
        prioClownKey = readKey(p, "prioClownKeyCodes", "prioClownKeyCode");
        prioGiantKey = readKey(p, "prioGiantKeyCodes", "prioGiantKeyCode");
        prioBabyKey = readKey(p, "prioBabyKeyCodes", "prioBabyKeyCode");
        closestKey = readKey(p, "closestKeyCodes", "closestKeyCode");
        holdLockKey = readKey(p, "holdLockKeyCodes", "holdLockKeyCode");
        toggleKey = readKey(p, "toggleKeyCodes", "toggleKeyCode");
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
        put(p, "prioClown", prioClown);
        put(p, "prioGiant", prioGiant);
        put(p, "prioBaby", prioBaby);
        put(p, "fov", clamp(fov, 30, 360));
        put(p, "showHud", showHud);
        put(p, "showKeyHints", showKeyHints);
        put(p, "closest", closest);
        put(p, "holdLock", holdLock);
        put(p, "insta", insta);
        put(p, "crits", clamp(crits, -0.2, 0.5));
        put(p, "vcrits", clamp(vcrits, 0.0, 1.0));
        put(p, "headFracMax", clamp(headFracMax, 0.01, 2.0));

        writeKey(p, "ignoreTooKeyCodes", "ignoreTooKeyCode", ignoreTooKey);
        writeKey(p, "ignoreGolemKeyCodes", "ignoreGolemKeyCode", ignoreGolemKey);
        writeKey(p, "ignoreSlimeKeyCodes", "ignoreSlimeKeyCode", ignoreSlimeKey);
        writeKey(p, "prioClownKeyCodes", "prioClownKeyCode", prioClownKey);
        writeKey(p, "prioGiantKeyCodes", "prioGiantKeyCode", prioGiantKey);
        writeKey(p, "prioBabyKeyCodes", "prioBabyKeyCode", prioBabyKey);
        writeKey(p, "closestKeyCodes", "closestKeyCode", closestKey);
        writeKey(p, "holdLockKeyCodes", "holdLockKeyCode", holdLockKey);
        writeKey(p, "toggleKeyCodes", "toggleKeyCode", toggleKey);
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
    public int[] getPrioBabyKey() { load(); return prioBabyKey.clone(); }
    public int[] getClosestKey() { load(); return closestKey.clone(); }
    public int[] getHoldLockKeyCodes() { load(); return holdLockKey.clone(); }
    public int[] getToggleKeyCodes() { load(); return toggleKey.clone(); }

    public void setIgnoreTooKey(int[] value) { ignoreTooKey = setKey(value); save(); }
    public void setIgnoreGolemKey(int[] value) { ignoreGolemKey = setKey(value); save(); }
    public void setIgnoreSlimeKey(int[] value) { ignoreSlimeKey = setKey(value); save(); }
    public void setPrioClownKey(int[] value) { prioClownKey = setKey(value); save(); }
    public void setPrioGiantKey(int[] value) { prioGiantKey = setKey(value); save(); }
    public void setPrioBabyKey(int[] value) { prioBabyKey = setKey(value); save(); }
    public void setClosestKey(int[] value) { closestKey = setKey(value); save(); }
    public void setHoldLockKeyCodes(int[] value) { holdLockKey = setKey(value); save(); }
    public void setToggleKeyCodes(int[] value) { toggleKey = setKey(value); save(); }

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

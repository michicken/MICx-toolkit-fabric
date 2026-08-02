package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Complete ZombiesAssist configuration migrated from the Forge zombies category. */
public final class ZombiesConfig {
    public boolean overlayEnabled = true;
    public boolean showPowerups = true;
    public boolean showStats = true;
    public boolean tooAlert = true;
    public boolean tooRushAlert = true;
    public boolean specialThreatHud = true;
    public boolean tooStrictGreen = true;
    public boolean postGameStats = true;
    public boolean ecoHints = true;
    public boolean pcRoundInfo = true;
    public boolean showMobs = true;
    public boolean puBeam = true;
    public boolean blockAlert = true;
    public boolean showEconomy = true;
    public boolean autoNotices = true;
    public boolean lsAssist = true;
    public boolean frCoach = true;
    public boolean waveTempo = true;
    public boolean slimeGrowth = true;
    public boolean noRotate = true;
    public boolean originalScoreboard = false;

    public int tacticalHudRight = 4;
    public int tacticalHudY = 32;
    public int ecoHudRight = 4;
    public int ecoHudCenterYOffset = 0;
    public int resourceLeftX = 8;
    public int resourceLeftY = 200;
    public int frHudXOffset = 0;
    public int frHudYOffset = 48;
    public int topHudXOffset = 0;
    public int topHudY = 2;
    public int lsHudXOffset = 0;
    public int lsHudYOffset = 0;
    public int threatHudXOffset = 18;
    public int threatHudYOffset = 8;
    public int puHudRight = 4;
    public int puHudBottom = 6;
    public int ecoClockRight = 4;
    public int ecoClockBottom = 8;
    public int tooRushXOffset = 0;
    public int tooRushYOffset = 0;
    public int blockAlertXOffset = 0;
    public int blockAlertYOffset = 14;

    public float tacticalHudScale = 1.0f;
    public float ecoHudScale = 1.0f;
    public float resourceHudScale = 1.0f;
    public float topHudScale = 1.0f;
    public float lsHudScale = 1.0f;
    public float threatHudScale = 1.0f;
    public float frHudScale = 1.0f;
    public float puHudScale = 1.0f;
    public float ecoClockScale = 1.0f;
    public float tooRushScale = 1.0f;
    public float blockAlertScale = 1.0f;

    private boolean loaded;

    public void load() {
        if (loaded) return;
        loaded = true;
        Path current = FabricRuntime.configPath().resolve("zombies-assist.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_Zombies.cfg");
        Properties properties = ConfigProperties.load(current, legacy);

        overlayEnabled = bool(properties, "overlayEnabled", true);
        showPowerups = bool(properties, "showPowerups", true);
        showStats = bool(properties, "showStats", true);
        tooAlert = bool(properties, "tooAlert", true);
        tooRushAlert = bool(properties, "tooRushAlert", true);
        specialThreatHud = bool(properties, "specialThreatHud", true);
        tooStrictGreen = bool(properties, "tooStrictGreen", true);
        postGameStats = bool(properties, "postGameStats", true);
        ecoHints = bool(properties, "ecoHints", true);
        pcRoundInfo = bool(properties, "pcRoundInfo", true);
        showMobs = bool(properties, "showMobs", true);
        puBeam = bool(properties, "puBeam", true);
        blockAlert = bool(properties, "blockAlert", true);
        showEconomy = bool(properties, "showEconomy", true);
        autoNotices = bool(properties, "autoNotices", true);
        lsAssist = bool(properties, "lsAssist", true);
        frCoach = bool(properties, "frCoach", true);
        waveTempo = bool(properties, "waveTempo", true);
        slimeGrowth = bool(properties, "slimeGrowth", true);
        noRotate = bool(properties, "noRotate", true);
        originalScoreboard = bool(properties, "originalScoreboard", false);

        tacticalHudRight = integer(properties, "tacticalHudRight", 4, 0, 9_999);
        tacticalHudY = integer(properties, "tacticalHudY", 32, 0, 9_999);
        ecoHudRight = integer(properties, "ecoHudRight", 4, 0, 9_999);
        ecoHudCenterYOffset = integer(properties, "ecoHudCenterYOffset", 0, -5_000, 5_000);
        resourceLeftX = integer(properties, "resourceLeftX", 8, 0, 9_999);
        resourceLeftY = integer(properties, "resourceLeftY", 200, 0, 9_999);
        frHudXOffset = integer(properties, "frHudXOffset", 0, -5_000, 5_000);
        frHudYOffset = integer(properties, "frHudYOffset", 48, -5_000, 5_000);
        topHudXOffset = integer(properties, "topHudXOffset", 0, -5_000, 5_000);
        topHudY = integer(properties, "topHudY", 2, -5_000, 5_000);
        lsHudXOffset = integer(properties, "lsHudXOffset", 0, -5_000, 5_000);
        lsHudYOffset = integer(properties, "lsHudYOffset", 0, -5_000, 5_000);
        threatHudXOffset = integer(properties, "threatHudXOffset", 18, -5_000, 5_000);
        threatHudYOffset = integer(properties, "threatHudYOffset", 8, -5_000, 5_000);
        puHudRight = integer(properties, "puHudRight", 4, 0, 9_999);
        puHudBottom = integer(properties, "puHudBottom", 6, 0, 9_999);
        ecoClockRight = integer(properties, "ecoClockRight", 4, 0, 9_999);
        ecoClockBottom = integer(properties, "ecoClockBottom", 8, 0, 9_999);
        tooRushXOffset = integer(properties, "tooRushXOffset", 0, -5_000, 5_000);
        tooRushYOffset = integer(properties, "tooRushYOffset", 0, -5_000, 5_000);
        blockAlertXOffset = integer(properties, "blockAlertXOffset", 0, -5_000, 5_000);
        blockAlertYOffset = integer(properties, "blockAlertYOffset", 14, -5_000, 5_000);

        tacticalHudScale = decimal(properties, "tacticalHudScale", 1.0f);
        ecoHudScale = decimal(properties, "ecoHudScale", 1.0f);
        resourceHudScale = decimal(properties, "resourceHudScale", 1.0f);
        topHudScale = decimal(properties, "topHudScale", 1.0f);
        lsHudScale = decimal(properties, "lsHudScale", 1.0f);
        threatHudScale = decimal(properties, "threatHudScale", 1.0f);
        frHudScale = decimal(properties, "frHudScale", 1.0f);
        puHudScale = decimal(properties, "puHudScale", 1.0f);
        ecoClockScale = decimal(properties, "ecoClockScale", 1.0f);
        tooRushScale = decimal(properties, "tooRushScale", 1.0f);
        blockAlertScale = decimal(properties, "blockAlertScale", 1.0f);
    }

    public void save() {
        Properties properties = new Properties();
        putBoolean(properties, "overlayEnabled", overlayEnabled);
        putBoolean(properties, "showPowerups", showPowerups);
        putBoolean(properties, "showStats", showStats);
        putBoolean(properties, "tooAlert", tooAlert);
        putBoolean(properties, "tooRushAlert", tooRushAlert);
        putBoolean(properties, "specialThreatHud", specialThreatHud);
        putBoolean(properties, "tooStrictGreen", tooStrictGreen);
        putBoolean(properties, "postGameStats", postGameStats);
        putBoolean(properties, "ecoHints", ecoHints);
        putBoolean(properties, "pcRoundInfo", pcRoundInfo);
        putBoolean(properties, "showMobs", showMobs);
        putBoolean(properties, "puBeam", puBeam);
        putBoolean(properties, "blockAlert", blockAlert);
        putBoolean(properties, "showEconomy", showEconomy);
        putBoolean(properties, "autoNotices", autoNotices);
        putBoolean(properties, "lsAssist", lsAssist);
        putBoolean(properties, "frCoach", frCoach);
        putBoolean(properties, "waveTempo", waveTempo);
        putBoolean(properties, "slimeGrowth", slimeGrowth);
        putBoolean(properties, "noRotate", noRotate);
        putBoolean(properties, "originalScoreboard", originalScoreboard);

        putInteger(properties, "tacticalHudRight", tacticalHudRight, 0, 9_999);
        putInteger(properties, "tacticalHudY", tacticalHudY, 0, 9_999);
        putInteger(properties, "ecoHudRight", ecoHudRight, 0, 9_999);
        putInteger(properties, "ecoHudCenterYOffset", ecoHudCenterYOffset, -5_000, 5_000);
        putInteger(properties, "resourceLeftX", resourceLeftX, 0, 9_999);
        putInteger(properties, "resourceLeftY", resourceLeftY, 0, 9_999);
        putInteger(properties, "frHudXOffset", frHudXOffset, -5_000, 5_000);
        putInteger(properties, "frHudYOffset", frHudYOffset, -5_000, 5_000);
        putInteger(properties, "topHudXOffset", topHudXOffset, -5_000, 5_000);
        putInteger(properties, "topHudY", topHudY, -5_000, 5_000);
        putInteger(properties, "lsHudXOffset", lsHudXOffset, -5_000, 5_000);
        putInteger(properties, "lsHudYOffset", lsHudYOffset, -5_000, 5_000);
        putInteger(properties, "threatHudXOffset", threatHudXOffset, -5_000, 5_000);
        putInteger(properties, "threatHudYOffset", threatHudYOffset, -5_000, 5_000);
        putInteger(properties, "puHudRight", puHudRight, 0, 9_999);
        putInteger(properties, "puHudBottom", puHudBottom, 0, 9_999);
        putInteger(properties, "ecoClockRight", ecoClockRight, 0, 9_999);
        putInteger(properties, "ecoClockBottom", ecoClockBottom, 0, 9_999);
        putInteger(properties, "tooRushXOffset", tooRushXOffset, -5_000, 5_000);
        putInteger(properties, "tooRushYOffset", tooRushYOffset, -5_000, 5_000);
        putInteger(properties, "blockAlertXOffset", blockAlertXOffset, -5_000, 5_000);
        putInteger(properties, "blockAlertYOffset", blockAlertYOffset, -5_000, 5_000);

        putDecimal(properties, "tacticalHudScale", tacticalHudScale);
        putDecimal(properties, "ecoHudScale", ecoHudScale);
        putDecimal(properties, "resourceHudScale", resourceHudScale);
        putDecimal(properties, "topHudScale", topHudScale);
        putDecimal(properties, "lsHudScale", lsHudScale);
        putDecimal(properties, "threatHudScale", threatHudScale);
        putDecimal(properties, "frHudScale", frHudScale);
        putDecimal(properties, "puHudScale", puHudScale);
        putDecimal(properties, "ecoClockScale", ecoClockScale);
        putDecimal(properties, "tooRushScale", tooRushScale);
        putDecimal(properties, "blockAlertScale", blockAlertScale);

        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("zombies-assist.properties"), properties,
                    "MICx ZombiesAssist configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save ZombiesAssist configuration", exception);
        }
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = ConfigProperties.string(properties, key, null);
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static int integer(Properties properties, String key, int fallback, int min, int max) {
        return ConfigProperties.integer(properties, key, fallback, min, max);
    }

    private static float decimal(Properties properties, String key, float fallback) {
        String value = ConfigProperties.string(properties, key, null);
        if (value == null) return fallback;
        try {
            float parsed = Float.parseFloat(value);
            if (!Float.isFinite(parsed)) return fallback;
            return clamp(parsed, 0.5f, 2.0f);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void putBoolean(Properties properties, String key, boolean value) {
        properties.setProperty(key, Boolean.toString(value));
    }

    private static void putInteger(Properties properties, String key, int value, int min, int max) {
        properties.setProperty(key, Integer.toString(Math.max(min, Math.min(max, value))));
    }

    private static void putDecimal(Properties properties, String key, float value) {
        float safe = Float.isFinite(value) ? clamp(value, 0.5f, 2.0f) : 1.0f;
        properties.setProperty(key, Float.toString(safe));
    }
}

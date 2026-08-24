package dev.micx.micxfabric;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Properties;

/** TeamSync configuration migrated from the Forge teamsync category. */
public final class TeamSyncConfig {
    public static final String DEFAULT_SERVER_URL = "wss://zombie.nienie.fun/zombies/ws";
    public static final String DEFAULT_TOKEN_URL = "https://zombie.nienie.fun/zombies/token";

    public String serverUrl = DEFAULT_SERVER_URL;
    public String tokenUrl = DEFAULT_TOKEN_URL;
    public int updateIntervalMs = 250;
    public int rosterCheckMs = 1_000;
    public boolean renderOverlay = true;
    public boolean renderWorld = true;
    public boolean localAimFallback = true;
    public int pingButton = -1;
    public boolean showPing = true;
    public int hudRightOffset = 150;
    public int hudY = 8;
    public float hudScaleX = 1.0f;
    public float hudScaleY = 1.0f;
    public int toggleKeyCode = GLFW.GLFW_KEY_J;

    private boolean loaded;

    public void load() {
        if (loaded) return;
        loaded = true;
        Path current = FabricRuntime.configPath().resolve("teamsync.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_TeamSync.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        serverUrl = safeUrl(ConfigProperties.string(properties, "serverUrl", DEFAULT_SERVER_URL),
                DEFAULT_SERVER_URL, "wss");
        tokenUrl = safeUrl(ConfigProperties.string(properties, "tokenUrl", DEFAULT_TOKEN_URL),
                DEFAULT_TOKEN_URL, "https");
        updateIntervalMs = ConfigProperties.integer(properties, "updateIntervalMs", 250, 50, 5_000);
        rosterCheckMs = ConfigProperties.integer(properties, "rosterCheckMs", 1_000, 500, 10_000);
        renderOverlay = ConfigProperties.bool(properties, "renderOverlay", true);
        renderWorld = ConfigProperties.bool(properties, "renderWorld", true);
        localAimFallback = ConfigProperties.bool(properties, "localAimFallback", true);
        pingButton = ConfigProperties.integer(properties, "pingButton", -1, -1, 2);
        showPing = ConfigProperties.bool(properties, "showPing", true);
        hudRightOffset = ConfigProperties.integer(properties, "hudRightOffset", 150, 20, 9_999);
        hudY = ConfigProperties.integer(properties, "hudY", 8, 0, 9_999);
        hudScaleX = scale(ConfigProperties.string(properties, "hudScaleX", "1.0"));
        hudScaleY = scale(ConfigProperties.string(properties, "hudScaleY", "1.0"));
        toggleKeyCode = ConfigProperties.integer(properties, "toggleKeyCode", GLFW.GLFW_KEY_J, -108, GLFW.GLFW_KEY_LAST);
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("serverUrl", safeUrl(serverUrl, DEFAULT_SERVER_URL, "wss"));
        properties.setProperty("tokenUrl", safeUrl(tokenUrl, DEFAULT_TOKEN_URL, "https"));
        properties.setProperty("updateIntervalMs", Integer.toString(clamp(updateIntervalMs, 50, 5_000)));
        properties.setProperty("rosterCheckMs", Integer.toString(clamp(rosterCheckMs, 500, 10_000)));
        properties.setProperty("renderOverlay", Boolean.toString(renderOverlay));
        properties.setProperty("renderWorld", Boolean.toString(renderWorld));
        properties.setProperty("localAimFallback", Boolean.toString(localAimFallback));
        properties.setProperty("pingButton", Integer.toString(clamp(pingButton, -1, 2)));
        properties.setProperty("showPing", Boolean.toString(showPing));
        properties.setProperty("hudRightOffset", Integer.toString(clamp(hudRightOffset, 20, 9_999)));
        properties.setProperty("hudY", Integer.toString(clamp(hudY, 0, 9_999)));
        properties.setProperty("hudScaleX", Float.toString(HudLayoutMath.clampScale(hudScaleX)));
        properties.setProperty("hudScaleY", Float.toString(HudLayoutMath.clampScale(hudScaleY)));
        properties.setProperty("toggleKeyCode", Integer.toString(clamp(toggleKeyCode, -108, GLFW.GLFW_KEY_LAST)));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("teamsync.properties"), properties,
                    "MICx TeamSync configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save TeamSync configuration", exception);
        }
    }

    static String safeUrl(String candidate, String fallback, String scheme) {
        try {
            URI uri = URI.create(candidate == null ? "" : candidate.trim());
            if (!scheme.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPath() == null || uri.getPath().isBlank()) return fallback;
            int port = uri.getPort();
            if (port != -1 && (("wss".equalsIgnoreCase(scheme) && port != 443)
                    || ("https".equalsIgnoreCase(scheme) && port != 443))) return fallback;
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float scale(String value) {
        try {
            return HudLayoutMath.clampScale(Float.parseFloat(value));
        } catch (RuntimeException ignored) {
            return 1.0f;
        }
    }
}

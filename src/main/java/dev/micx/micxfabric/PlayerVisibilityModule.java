package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Hides or marks nearby players while preserving self and sleeping-player visibility. */
public final class PlayerVisibilityModule implements Module {
    private static final PlayerVisibilityModule INSTANCE = new PlayerVisibilityModule();
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_G;
    private boolean enabled;
    private boolean active = true;
    private boolean hideMode;
    private float opacity = 0.15f;
    private float range = (float) Math.sqrt(2.0);
    private int keyCode = DEFAULT_KEY;
    private boolean configLoaded;

    private PlayerVisibilityModule() {
    }

    public static PlayerVisibilityModule instance() {
        return INSTANCE;
    }

    public static boolean shouldHide(AbstractClientPlayer target, Minecraft client) {
        return INSTANCE.enabled && INSTANCE.active && INSTANCE.hideMode && INSTANCE.inRange(target, client);
    }

    public static int modelTint(AbstractClientPlayer target, Minecraft client) {
        if (!INSTANCE.enabled || !INSTANCE.active || INSTANCE.hideMode || !INSTANCE.inRange(target, client)) return -1;
        int alpha = Math.max(1, Math.min(255, Math.round(INSTANCE.opacity * 255.0f)));
        return (alpha << 24) | 0x00FFFFFF;
    }

    private boolean inRange(AbstractClientPlayer target, Minecraft client) {
        if (target == null || client == null || client.player == null || target == client.player
                || target.isSleeping() || target.isRemoved()) return false;
        return client.player.distanceToSqr(target) <= range * range;
    }

    @Override
    public String id() {
        return "player_visibility";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (!enabled) active = false;
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return new InputBinding(keyCode);
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (!newlyEnabled) active = !active;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("PlayerVisibility: " + (active ? "ON" : "OFF")));
        }
        saveConfig();
    }

    public boolean active() {
        return active;
    }

    public boolean hideMode() {
        return hideMode;
    }

    public float opacity() {
        return opacity;
    }

    public float range() {
        return range;
    }

    public int keyCode() {
        loadConfig();
        return keyCode;
    }

    public void setActive(boolean value) {
        active = value;
        saveConfig();
    }

    public void setHideMode(boolean value) {
        hideMode = value;
        saveConfig();
    }

    public void setOpacity(float value) {
        opacity = Math.max(0.05f, Math.min(1.0f, value));
        saveConfig();
    }

    public void setRange(float value) {
        range = Math.max(0.5f, Math.min(64.0f, value));
        saveConfig();
    }

    public void setKeyCode(int value) {
        keyCode = value == 0 ? GLFW.GLFW_KEY_UNKNOWN : Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, value));
        saveConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("player-visibility.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_PlayerVisibility.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        active = ConfigProperties.bool(properties, "active", true);
        hideMode = ConfigProperties.bool(properties, "hideMode", false);
        opacity = parseFloat(properties.getProperty("opacity"), 0.15f, 0.05f, 1.0f);
        float rangeSq = parseFloat(properties.getProperty("rangeSq"), 2.0f, 0.25f, 4096.0f);
        range = (float) Math.sqrt(rangeSq);
        keyCode = ConfigProperties.integer(properties, "keyCode", DEFAULT_KEY, -108, GLFW.GLFW_KEY_LAST);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("active", Boolean.toString(active));
        properties.setProperty("hideMode", Boolean.toString(hideMode));
        properties.setProperty("opacity", Float.toString(opacity));
        properties.setProperty("rangeSq", Float.toString(range * range));
        properties.setProperty("keyCode", Integer.toString(keyCode));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("player-visibility.properties"), properties,
                    "MICx PlayerVisibility configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save PlayerVisibility configuration", exception);
        }
    }

    private static float parseFloat(String value, float fallback, float min, float max) {
        try {
            return Math.max(min, Math.min(max, Float.parseFloat(value)));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

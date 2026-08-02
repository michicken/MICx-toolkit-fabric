package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Native 26.2 player outline target selector. Thickness remains vanilla-controlled. */
public final class PlayerOutlineEspModule implements Module {
    private static final PlayerOutlineEspModule INSTANCE = new PlayerOutlineEspModule();
    private boolean enabled;
    private boolean active = true;
    private float range = 128.0f;
    private boolean configLoaded;

    private PlayerOutlineEspModule() {
    }

    public static PlayerOutlineEspModule instance() {
        return INSTANCE;
    }

    public static boolean shouldOutline(AbstractClientPlayer player, Minecraft client) {
        return INSTANCE.enabled && INSTANCE.active && INSTANCE.inRange(player, client);
    }

    /** Returns whether the current frame has at least one eligible outline target. */
    public static boolean hasTarget(Minecraft client) {
        if (client == null || client.level == null) return false;
        for (AbstractClientPlayer player : client.level.players()) {
            if (shouldOutline(player, client)) return true;
        }
        return false;
    }

    @Override
    public String id() {
        return "player_outline_esp";
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
        ModuleStateStore.put(id(), enabled);
    }

    public boolean active() { return active; }
    public float range() { loadConfig(); return range; }
    public void setActive(boolean value) { active = value; saveConfig(); }
    public void setRange(float value) { range = clamp(value, 8.0f, 256.0f); saveConfig(); }

    private boolean inRange(AbstractClientPlayer player, Minecraft client) {
        return player != null && client != null && client.player != null && player != client.player
                && !player.isRemoved() && client.player.distanceToSqr(player) <= range * range;
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("player-outline-esp.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_PlayerOutlineESP.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        active = ConfigProperties.bool(properties, "active", true);
        range = parseFloat(properties.getProperty("range"), 128.0f, 8.0f, 256.0f);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("active", Boolean.toString(active));
        properties.setProperty("range", Float.toString(range));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("player-outline-esp.properties"), properties,
                    "MICx PlayerOutlineESP configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save PlayerOutlineESP configuration", exception);
        }
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float parseFloat(String value, float fallback, float min, float max) {
        try { return clamp(Float.parseFloat(value), min, max); }
        catch (Exception ignored) { return fallback; }
    }
}

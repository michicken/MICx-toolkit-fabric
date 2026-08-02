package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Crosshair health panel migrated from ToroHealth's target and health-bar behavior. */
public final class ToroHealthModule implements Module {
    private static final ToroHealthModule INSTANCE = new ToroHealthModule();
    private boolean enabled;
    private boolean overlayEnabled = true;
    private long hideDelayMs = 200L;
    private int displayMode;
    private int displayPosition;
    private int displayX;
    private int displayY;
    private boolean showDamageParticles = true;
    private int damageColor = 0xFF0000;
    private int healColor = 0x00FF00;
    private int targetId = Integer.MIN_VALUE;
    private LivingEntity target;
    private long targetSeenAt;
    private Object activeLevel;
    private boolean configLoaded;

    private ToroHealthModule() {
    }

    public static ToroHealthModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "toro_health";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (!enabled) clearTarget();
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public void resetState() {
        clearTarget();
        activeLevel = null;
    }

    @Override
    public void tick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            clearTarget();
            return;
        }
        if (activeLevel != client.level) {
            activeLevel = client.level;
            clearTarget();
        }
        if (!enabled || !overlayEnabled) return;
        Entity pointed = client.crosshairPickEntity;
        if (pointed instanceof LivingEntity living && isTarget(living)) {
            target = living;
            targetSeenAt = System.currentTimeMillis();
        } else if (target != null && System.currentTimeMillis() - targetSeenAt > hideDelayMs) {
            clearTarget();
        }
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !overlayEnabled || target == null || target.isRemoved() || target.getHealth() <= 0) return;
        Minecraft client = Minecraft.getInstance();
        float health = Math.max(0.0f, target.getHealth());
        float max = Math.max(1.0f, target.getMaxHealth());
        float ratio = Math.max(0.0f, Math.min(1.0f, health / max));
        String name = target.getName().getString();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (displayMode == 2) return;
        int x = width / 2 - 50;
        int y = 4;
        if (displayPosition == 1) { x = 2; y = 4; }
        else if (displayPosition == 2) { x = width - 102; y = 4; }
        else if (displayPosition == 3) { x = 2; y = height - 30; }
        else if (displayPosition == 4) { x = width - 102; y = height - 30; }
        else if (displayPosition == 5) { x = displayX; y = displayY; }
        if (displayMode == 1) {
            graphics.text(client.font, Component.literal(name + "  " + (int) health + "/" + (int) max), x + 8, y + 4,
                    healthColor(ratio), true);
            return;
        }
        graphics.text(client.font, Component.literal(name), x + 8, y + 4, 0xFFFFFFFF, true);
        int barX = x + 7;
        int barY = y + 16;
        int barW = 84;
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + 7, 0xFF222222);
        graphics.fill(barX, barY, barX + barW, barY + 6, 0xBB111111);
        graphics.fill(barX, barY, barX + Math.round(barW * ratio), barY + 6, healthColor(ratio));
        float absorption = Math.max(0.0f, target.getAbsorptionAmount());
        if (absorption > 0.0f) {
            int shieldWidth = Math.min(barW - Math.round(barW * ratio), Math.round(barW * absorption / max));
            int start = barX + Math.round(barW * ratio);
            graphics.fill(start, barY, start + shieldWidth, barY + 6, 0xFFFFCC44);
        }
        String hp = (int) health + "/" + (int) max;
        graphics.text(client.font, Component.literal(hp), barX + (barW - client.font.width(hp)) / 2, barY + 1,
                ratio > .5f ? 0xFFFFFFFF : ratio > .25f ? 0xFFFFFFAA : 0xFFFFAAAA, true);
    }

    private static boolean isTarget(LivingEntity living) {
        return !(living instanceof Player) && !(living instanceof ArmorStand)
                && !living.isDeadOrDying() && living.getHealth() > 0.0f;
    }

    private void clearTarget() {
        target = null;
        targetId = Integer.MIN_VALUE;
        targetSeenAt = 0L;
    }

    private static int healthColor(float ratio) {
        if (ratio > .5f) {
            float t = (1.0f - ratio) * 2.0f;
            return 0xFF000000 | ((int) (255 * t) << 16) | 0xFF00;
        }
        return 0xFFFF0000 | ((int) (255 * ratio * 2.0f) << 8);
    }

    public boolean overlayEnabled() {
        return overlayEnabled;
    }

    public void setOverlayEnabled(boolean value) {
        overlayEnabled = value;
        saveConfig();
    }

    public int displayMode() {
        return displayMode;
    }

    public int displayPosition() {
        return displayPosition;
    }

    public int displayX() {
        return displayX;
    }

    public int displayY() {
        return displayY;
    }

    public boolean showDamageParticles() {
        return showDamageParticles;
    }

    public int damageColor() {
        return damageColor;
    }

    public int healColor() {
        return healColor;
    }

    public long hideDelayMs() {
        return hideDelayMs;
    }

    public void setDisplayMode(int value) {
        displayMode = Math.max(0, Math.min(2, value));
        saveConfig();
    }

    public void setDisplayPosition(int value) {
        displayPosition = Math.max(0, Math.min(5, value));
        saveConfig();
    }

    public void setDisplayX(int value) {
        displayX = Math.max(-20_000, Math.min(20_000, value));
        saveConfig();
    }

    public void setDisplayY(int value) {
        displayY = Math.max(-20_000, Math.min(20_000, value));
        saveConfig();
    }

    public void setShowDamageParticles(boolean value) {
        showDamageParticles = value;
        saveConfig();
    }

    public void setDamageColor(int value) {
        damageColor = value & 0xFFFFFF;
        saveConfig();
    }

    public void setHealColor(int value) {
        healColor = value & 0xFFFFFF;
        saveConfig();
    }

    public void setHideDelayMs(long value) {
        hideDelayMs = Math.max(50L, Math.min(5_000L, value));
        saveConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("toro-health.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ToroHealth.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        overlayEnabled = ConfigProperties.bool(properties, "overlayEnabled", true);
        displayMode = ConfigProperties.integer(properties, "displayMode", 0, 0, 2);
        displayPosition = ConfigProperties.integer(properties, "displayPosition", 0, 0, 5);
        displayX = ConfigProperties.integer(properties, "displayX", 0, -20_000, 20_000);
        displayY = ConfigProperties.integer(properties, "displayY", 0, -20_000, 20_000);
        showDamageParticles = ConfigProperties.bool(properties, "showDamageParticles", true);
        damageColor = parseColor(properties.getProperty("damageColor"), 0xFF0000);
        healColor = parseColor(properties.getProperty("healColor"), 0x00FF00);
        hideDelayMs = ConfigProperties.integer(properties, "hideDelayMs", 200, 50, 5_000);
    }

    private static int parseColor(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            String normalized = value.trim();
            if (normalized.startsWith("#")) normalized = normalized.substring(1);
            if (normalized.startsWith("0x") || normalized.startsWith("0X")) normalized = normalized.substring(2);
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            try {
                return Integer.parseInt(value.trim()) & 0xFFFFFF;
            } catch (NumberFormatException ignoredAgain) {
                return fallback;
            }
        }
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("overlayEnabled", Boolean.toString(overlayEnabled));
        properties.setProperty("displayMode", Integer.toString(displayMode));
        properties.setProperty("displayPosition", Integer.toString(displayPosition));
        properties.setProperty("displayX", Integer.toString(displayX));
        properties.setProperty("displayY", Integer.toString(displayY));
        properties.setProperty("showDamageParticles", Boolean.toString(showDamageParticles));
        properties.setProperty("damageColor", Integer.toString(damageColor));
        properties.setProperty("healColor", Integer.toString(healColor));
        properties.setProperty("hideDelayMs", Long.toString(hideDelayMs));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("toro-health.properties"), properties,
                    "MICx ToroHealth configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save ToroHealth configuration", exception);
        }
    }
}

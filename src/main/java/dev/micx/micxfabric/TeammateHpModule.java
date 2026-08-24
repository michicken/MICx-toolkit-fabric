package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** Teammate health cards migrated from the Forge overlay's loaded-player path. */
public final class TeammateHpModule implements Module {
    private static final TeammateHpModule INSTANCE = new TeammateHpModule();
    private static final int CARD_HEIGHT = 26;
    private static final int CARD_GAP = 2;
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_H;

    private boolean enabled;
    private boolean active = true;
    private boolean overlayEnabled = true;
    private boolean showHidden;
    private boolean showDistance = true;
    private int cardWidth = 180;
    private int bgAlpha = 70;
    private int screenX = 8;
    private int screenY = 40;
    private float uiScale = 1.0f;
    private int keyCode = DEFAULT_KEY;
    private boolean configLoaded;
    private Object activeLevel;

    private TeammateHpModule() {
    }

    public static TeammateHpModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "teammate_hp";
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
        if (!enabled) activeLevel = null;
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return new InputBinding(keyCode);
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        loadConfig();
        if (!newlyEnabled || !active) active = !active;
        saveConfig();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("TeammateHP: " + (active ? "ON" : "OFF")));
        }
    }

    @Override
    public void resetState() {
        activeLevel = null;
    }

    @Override
    public void tick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            activeLevel = null;
            return;
        }
        if (activeLevel != client.level) activeLevel = client.level;
    }

    public boolean active() {
        return active;
    }

    public boolean overlayEnabled() {
        return overlayEnabled;
    }

    public boolean showHidden() {
        return showHidden;
    }

    public boolean showDistance() {
        return showDistance;
    }

    public int cardWidth() {
        return cardWidth;
    }

    public int bgAlpha() {
        return bgAlpha;
    }

    public int screenX() {
        return screenX;
    }

    public int screenY() {
        return screenY;
    }

    public float uiScale() {
        return uiScale;
    }

    public int keyCode() {
        loadConfig();
        return keyCode;
    }

    public void setActive(boolean value) {
        active = value;
        saveConfig();
    }

    public void setOverlayEnabled(boolean value) {
        overlayEnabled = value;
        saveConfig();
    }

    public void setShowHidden(boolean value) {
        showHidden = value;
        saveConfig();
    }

    public void setShowDistance(boolean value) {
        showDistance = value;
        saveConfig();
    }

    public void setCardWidth(int value) {
        cardWidth = clamp(value, 100, 400);
        saveConfig();
    }

    public void setBgAlpha(int value) {
        bgAlpha = clamp(value, 0, 200);
        saveConfig();
    }

    public void setScreenX(int value) {
        screenX = clamp(value, 0, 9_999);
        saveConfig();
    }

    public void setScreenY(int value) {
        screenY = clamp(value, 0, 9_999);
        saveConfig();
    }

    public void setLayoutPosition(int x, int y) {
        loadConfig();
        screenX = clamp(x, 0, 9_999);
        screenY = clamp(y, 0, 9_999);
    }

    public void saveLayoutConfiguration() {
        loadConfig();
        saveConfig();
    }

    public void setUiScale(float value) {
        uiScale = clamp(value, 0.5f, 1.75f);
        saveConfig();
    }

    public void setKeyCode(int value) {
        keyCode = value == 0 ? GLFW.GLFW_KEY_UNKNOWN : clamp(value, -108, GLFW.GLFW_KEY_LAST);
        saveConfig();
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !active || !overlayEnabled) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;

        List<AbstractClientPlayer> players = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractClientPlayer player) || player.isRemoved()) continue;
            if (player != client.player && !showHidden && player.isInvisible()) continue;
            players.add(player);
        }
        players.sort(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER));
        players.remove(client.player);
        players.add(0, client.player);
        if (players.size() > 4) players = new ArrayList<>(players.subList(0, 4));
        if (players.isEmpty()) return;

        float scaleX = HudLayoutRegistry.scaleX("teammate_hp", uiScale);
        float scaleY = HudLayoutRegistry.scaleY("teammate_hp", uiScale);
        int x = Math.round(screenX / scaleX);
        int y = Math.round(screenY / scaleY);
        int scaleMax = 20;
        for (AbstractClientPlayer player : players) {
            scaleMax = Math.max(scaleMax, Math.round(Math.max(1.0f, player.getMaxHealth())));
        }

        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            for (AbstractClientPlayer player : players) {
                drawCard(graphics, client, player, x, y, scaleMax);
                y += CARD_HEIGHT + CARD_GAP;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, Minecraft client,
                          AbstractClientPlayer player, int x, int y, int scaleMax) {
        float max = Math.max(1.0f, player.getMaxHealth());
        float health = Math.max(0.0f, Math.min(max, player.getHealth()));
        float absorption = Math.max(0.0f, player.getAbsorptionAmount());
        float ratio = health / max;
        int accent = player == client.player ? 0xFF58A6FF : healthColor(ratio);
        int background = (Math.max(0, Math.min(200, bgAlpha)) << 24) | 0x161616;
        int cardRight = x + cardWidth;
        int textLeft = x + 9;
        int right = cardRight - 6;

        graphics.fill(x - 1, y + 1, cardRight + 1, y + CARD_HEIGHT + 1, 0x40000000);
        graphics.fill(x - 1, y - 1, cardRight + 1, y + CARD_HEIGHT + 1, 0xFF1A1A1A);
        graphics.fill(x, y, cardRight, y + CARD_HEIGHT, background);
        graphics.fill(x, y, x + 3, y + CARD_HEIGHT, accent);

        String name = player.getName().getString();
        int nameWidth = Math.max(20, (cardWidth - 21) * 55 / 100);
        if (showDistance && player != client.player) nameWidth = Math.max(20, nameWidth - 30);
        name = trim(client, name, nameWidth);
        graphics.text(client.font, Component.literal(name), textLeft, y + 4, 0xFFE7EAEE, true);

        String total = Integer.toString(Math.round(health + absorption)) + "/" + Math.round(max);
        int totalWidth = client.font.width(total);
        graphics.text(client.font, Component.literal(total), right - totalWidth, y + 4,
                ratio >= .6f ? 0xFF57B98C : ratio >= .4f ? 0xFFE8A73E : 0xFFE06A6A, true);

        int barY = y + CARD_HEIGHT - 11;
        int barLeft = x + 9;
        int barRight = Math.max(barLeft + 10, right);
        int barWidth = barRight - barLeft;
        graphics.fill(barLeft, barY, barRight, barY + 6, 0x66000000);
        int fill = Math.round(barWidth * health / scaleMax);
        if (fill > 0) graphics.fill(barLeft, barY, Math.min(barRight, barLeft + fill), barY + 6, healthColor(ratio));
        if (absorption > 0.0f) {
            int shield = Math.min(barWidth - fill, Math.round(barWidth * absorption / scaleMax));
            if (shield > 0) graphics.fill(Math.min(barRight, barLeft + fill), barY,
                    Math.min(barRight, barLeft + fill + shield), barY + 6, 0xFFFFCC44);
        }

        if (showDistance && player != client.player) {
            String distance = String.format("+%.1fm", client.player.distanceTo(player));
            int distanceWidth = client.font.width(distance);
            if (distanceWidth + totalWidth + 8 < cardWidth - 12) {
                graphics.text(client.font, Component.literal(distance), right - totalWidth - distanceWidth - 4,
                        y + 4, 0xFF98A0AB, false);
            }
        }
    }

    private static String trim(Minecraft client, String value, int width) {
        return client.font.width(value) <= width ? value : client.font.plainSubstrByWidth(value, width, true);
    }

    private static int healthColor(float ratio) {
        if (ratio > .5f) {
            float t = (1.0f - ratio) * 2.0f;
            return 0xFF000000 | ((int) (255 * t) << 16) | 0xFF00;
        }
        return 0xFFFF0000 | ((int) (255 * ratio * 2.0f) << 8);
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("teammate-hp.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_TeammateHP.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        overlayEnabled = ConfigProperties.bool(properties, "overlayEnabled", true);
        showHidden = ConfigProperties.bool(properties, "showHidden", false);
        showDistance = ConfigProperties.bool(properties, "showDistance", true);
        cardWidth = ConfigProperties.integer(properties, "cardWidth", 180, 100, 400);
        bgAlpha = ConfigProperties.integer(properties, "bgAlpha", 70, 0, 200);
        screenX = ConfigProperties.integer(properties, "screenX", 8, 0, 9_999);
        screenY = ConfigProperties.integer(properties, "screenY", 40, 0, 9_999);
        uiScale = parseFloat(properties.getProperty("uiScale"), 1.0f, 0.5f, 1.75f);
        keyCode = ConfigProperties.integer(properties, "keyCode", DEFAULT_KEY, -108, GLFW.GLFW_KEY_LAST);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("overlayEnabled", Boolean.toString(overlayEnabled));
        properties.setProperty("showHidden", Boolean.toString(showHidden));
        properties.setProperty("showDistance", Boolean.toString(showDistance));
        properties.setProperty("cardWidth", Integer.toString(cardWidth));
        properties.setProperty("bgAlpha", Integer.toString(bgAlpha));
        properties.setProperty("screenX", Integer.toString(screenX));
        properties.setProperty("screenY", Integer.toString(screenY));
        properties.setProperty("uiScale", Float.toString(uiScale));
        properties.setProperty("keyCode", Integer.toString(keyCode));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("teammate-hp.properties"), properties,
                    "MICx TeammateHP configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save TeammateHP configuration", exception);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float parseFloat(String value, float fallback, float min, float max) {
        try {
            return clamp(Float.parseFloat(value), min, max);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

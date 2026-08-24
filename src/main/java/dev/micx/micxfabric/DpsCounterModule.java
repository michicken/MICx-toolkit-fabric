package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Properties;

/** One-second client-side damage window based on health and absorption deltas. */
public final class DpsCounterModule implements Module {
    private static final DpsCounterModule INSTANCE = new DpsCounterModule();
    private static final long WINDOW_MS = 1_000L;
    private final Map<Integer, HealthSnapshot> previous = new HashMap<>();
    private final ArrayDeque<DamageSample> damage = new ArrayDeque<>();
    private boolean enabled;
    private boolean overlayEnabled = true;
    private int hudRight = 4;
    private int hudY = 2;
    private float hudScaleX = 1.0f;
    private float hudScaleY = 1.0f;
    private boolean configLoaded;
    private Object activeLevel;
    private float cachedDamage;

    private DpsCounterModule() {
    }

    public static DpsCounterModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "dps_counter";
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
        this.enabled = enabled;
        if (!enabled) clearTracking();
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public void tick(Minecraft client) {
        if (client == null || client.level == null) {
            clearTracking();
            return;
        }
        if (activeLevel != client.level) {
            clearTracking();
            activeLevel = client.level;
        }
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living.isRemoved()) continue;
            int id = living.getId();
            seen.add(id);
            float health = Math.max(0.0f, living.getHealth());
            float absorption = Math.max(0.0f, living.getAbsorptionAmount());
            HealthSnapshot prior = previous.put(id, new HealthSnapshot(health, absorption));
            if (prior == null) continue;
            float delta = Math.max(0.0f, prior.health - health)
                    + Math.max(0.0f, prior.absorption - absorption);
            if (delta > 0.0f) damage.addLast(new DamageSample(System.currentTimeMillis(), delta));
        }
        previous.keySet().removeIf(id -> !seen.contains(id));
        evictExpired(System.currentTimeMillis());
    }

    public int dps() {
        evictExpired(System.currentTimeMillis());
        return Math.max(0, Math.round(cachedDamage));
    }

    public boolean overlayEnabled() {
        loadConfig();
        return overlayEnabled;
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        loadConfig();
        this.overlayEnabled = overlayEnabled;
        saveConfig();
    }

    public int hudRight() {
        loadConfig();
        return hudRight;
    }

    public int hudY() {
        loadConfig();
        return hudY;
    }

    public float hudScaleX() {
        loadConfig();
        return hudScaleX;
    }

    public float hudScaleY() {
        loadConfig();
        return hudScaleY;
    }

    public void setHudPosition(int right, int y) {
        loadConfig();
        hudRight = Math.max(0, Math.min(9_999, right));
        hudY = Math.max(0, Math.min(9_999, y));
    }

    public void setHudScale(float x, float y) {
        loadConfig();
        hudScaleX = HudLayoutMath.clampScale(x);
        hudScaleY = HudLayoutMath.clampScale(y);
    }

    public void saveLayoutConfiguration() {
        loadConfig();
        saveConfig();
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !overlayEnabled()) return;
        int value = dps();
        int color = value > 10 ? 0xFF55D68B : value > 0 ? 0xFFE8A73E : 0xFFE06A6A;
        String text = "OBS DPS " + value;
        Minecraft client = Minecraft.getInstance();
        float sx = HudLayoutRegistry.scaleX("dps_counter", hudScaleX());
        float sy = HudLayoutRegistry.scaleY("dps_counter", hudScaleY());
        graphics.pose().pushMatrix();
        graphics.pose().scale(sx, sy);
        try {
            int x = Math.max(4, Math.round((graphics.guiWidth() - hudRight()) / sx) - client.font.width(text));
            int y = Math.round(hudY() / sy);
            graphics.text(client.font, Component.literal(text), x, y, color, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path file = FabricRuntime.configPath().resolve("dps-counter.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_DPSCounter.cfg");
        Properties properties = ConfigProperties.load(file, legacy);
        overlayEnabled = ConfigProperties.bool(properties, "overlayEnabled", true);
        hudRight = ConfigProperties.integer(properties, "hudRight", 4, 0, 9_999);
        hudY = ConfigProperties.integer(properties, "hudY", 2, 0, 9_999);
        hudScaleX = parseScale(properties.getProperty("hudScaleX"));
        hudScaleY = parseScale(properties.getProperty("hudScaleY"));
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("overlayEnabled", Boolean.toString(overlayEnabled));
        properties.setProperty("hudRight", Integer.toString(hudRight));
        properties.setProperty("hudY", Integer.toString(hudY));
        properties.setProperty("hudScaleX", Float.toString(hudScaleX));
        properties.setProperty("hudScaleY", Float.toString(hudScaleY));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("dps-counter.properties"), properties,
                    "MICx DPS counter configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save DPS counter configuration", exception);
        }
    }

    private static float parseScale(String value) {
        try {
            return HudLayoutMath.clampScale(Float.parseFloat(value));
        } catch (RuntimeException ignored) {
            return 1.0f;
        }
    }

    private void evictExpired(long now) {
        long cutoff = now - WINDOW_MS;
        while (!damage.isEmpty() && damage.peekFirst().timestamp < cutoff) damage.removeFirst();
        float total = 0.0f;
        for (DamageSample sample : damage) total += sample.amount;
        cachedDamage = total;
    }

    @Override
    public void resetState() {
        clearTracking();
    }

    private void clearTracking() {
        previous.clear();
        damage.clear();
        cachedDamage = 0.0f;
        activeLevel = null;
    }

    private record HealthSnapshot(float health, float absorption) {
    }

    private record DamageSample(long timestamp, float amount) {
    }
}

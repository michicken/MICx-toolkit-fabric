package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * One-second client-side damage window based on health and absorption deltas.
 * Hud: Top-right 3-line block (OBS DPS + RC + GS) matching Forge DpsCounterModule stacking
 * — OBS DPS at dpsHudTop (red/yellow/green by value), RC at yOff=yTop+10, GS at yOff+10.
 */
public final class DpsCounterModule implements Module {
    private static final DpsCounterModule INSTANCE = new DpsCounterModule();
    private static final long WINDOW_MS = 1_000L;
    private final java.util.Map<Integer, HealthSnapshot> previous = new java.util.HashMap<>();
    private final java.util.ArrayDeque<DamageSample> damage = new java.util.ArrayDeque<>();
    private boolean enabled;
    private boolean overlayEnabled = true;
    private int hudRight = 4;
    private int hudY = 2;
    private float hudScaleX = 1.0f;
    private float hudScaleY = 1.0f;
    private boolean configLoaded;
    private boolean drawLogged;
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
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || living.isRemoved()) continue;
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
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;
        float sx = HudLayoutRegistry.scaleX("dps_counter", hudScaleX());
        float sy = HudLayoutRegistry.scaleY("dps_counter", hudScaleY());
        graphics.pose().pushMatrix();
        graphics.pose().scale(sx, sy);
        try {
            int yOff = Math.round(hudY() / sy);
            int right = Math.round((graphics.guiWidth() - hudRight()) / sx);
            // Line 1: OBS DPS + value — 0 pink (#E06A6A), 1-10 amber, >10 green (matches pre-fix, Forge does red/yellow/green)
            int value = dps();
            int color = value > 10 ? 0xFF55D68B : value > 0 ? 0xFFE8A73E : 0xFFE06A6A;
            String text = "OBS DPS " + value;
            int w = client.font.width(text);
            graphics.text(client.font, Component.literal(text), right - w, yOff, color, true);
            yOff += 10;

            // RC: RC active toggle (mouse middle), not enabled() — else always ON
            boolean rcOn = RightClickerModule.isActive();
            String rcLine = "§eRC§0:§r " + (rcOn ? "§a" : "§c") + (rcOn ? "ON" : "OFF");
            // 渲染走 drawRightAligned 逐段 literal+color 路径（方法内统一补 FF alpha）。
            // 真根因（javap 实证）：26.2 GuiGraphicsExtractor.text 在 ARGB.alpha(color)==0
            // 时直接 return 不绘制——此前 0xFFFFFF 基色无 alpha 位，整段被丢弃。
            net.minecraft.network.chat.Component rcComp = LegacyText.of(rcLine);
            int rw = client.font.width(rcComp);
            ZombiesAssistModule.drawRightAligned(graphics, client, rcLine, right, yOff);

            // GS: always show mode number (23/234/24/34), green when active red when not — never "OFF"
            // 对齐 Forge kbc.isActive()：连点 toggle 开关状态。modeIndex==0=未激活（关闭时
            // modeName() 返回的是预选模式名，不能用来判红绿，否则恒绿）
            KeyboardClickerModule kbc = KeyboardClickerModule.instance();
            boolean gsOn = kbc.modeIndex() != 0;
            String gsVal = kbc.modeName();
            if (gsVal == null || "OFF".equals(gsVal)) {
                gsVal = kbc.isMode23() ? "23" : kbc.isMode234() ? "234" : kbc.isMode24() ? "24" : kbc.isMode34() ? "34" : "23";
            }
            String gsLine = "§eGS§0:§r " + (gsOn ? "§a" : "§c") + gsVal;
            net.minecraft.network.chat.Component gsComp = LegacyText.of(gsLine);
            int gw = client.font.width(gsComp);
            ZombiesAssistModule.drawRightAligned(graphics, client, gsLine, right, yOff + 10);
            if (!drawLogged) {
                drawLogged = true;
                MicxFabric.LOGGER.info(
                        "[micx-dps] draw: guiW={} sx={} sy={} yOff={} right={} dpsW={} rcW={} gsW={} rcOn={} gsOn={} gsVal={}",
                        graphics.guiWidth(), sx, sy, yOff, right, w, rw, gw, rcOn, gsOn, gsVal);
            }
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

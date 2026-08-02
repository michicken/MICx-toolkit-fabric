package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** One-second client-side damage window based on health and absorption deltas. */
public final class DpsCounterModule implements Module {
    private static final DpsCounterModule INSTANCE = new DpsCounterModule();
    private static final long WINDOW_MS = 1_000L;
    private final Map<Integer, HealthSnapshot> previous = new HashMap<>();
    private final ArrayDeque<DamageSample> damage = new ArrayDeque<>();
    private boolean enabled;
    private boolean overlayEnabled = true;
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
        return overlayEnabled;
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        this.overlayEnabled = overlayEnabled;
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !overlayEnabled) return;
        int value = dps();
        int color = value > 10 ? 0xFF55D68B : value > 0 ? 0xFFE8A73E : 0xFFE06A6A;
        String text = "OBS DPS " + value;
        int x = Math.max(4, graphics.guiWidth() - Minecraft.getInstance().font.width(text) - 4);
        graphics.text(Minecraft.getInstance().font, Component.literal(text), x, 2, color, true);
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

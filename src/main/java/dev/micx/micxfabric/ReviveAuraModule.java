package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public final class ReviveAuraModule implements Module {
    private static final ReviveAuraModule INSTANCE = new ReviveAuraModule();
    private boolean enabled;
    private double range = 4.5;
    private double intervalMs = 200.0;
    private long lastRevive = 0L;
    private boolean configLoaded;

    private ReviveAuraModule() {}

    public static ReviveAuraModule instance() { return INSTANCE; }

    @Override public String id() { return "revive_aura"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
        if (v) lastRevive = 0L;
    }

    public double getRange() { loadConfig(); return range; }
    public void setRange(double v) { range = Math.max(1.0, Math.min(10.0, v)); saveConfig(); }
    public double getIntervalMs() { loadConfig(); return intervalMs; }
    public void setIntervalMs(double v) { intervalMs = Math.max(50.0, Math.min(1000.0, v)); saveConfig(); }

    @Override public void tick(Minecraft client) {
        if (!enabled || client == null || client.player == null || client.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastRevive < intervalMs) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player player)) continue;
            if (player == client.player) continue;
            if (!player.isSleeping()) continue;
            if (client.player.distanceTo(player) > range) continue;
            if (client.getConnection() == null) return;
            client.getConnection().send(new ServerboundInteractPacket(player.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));
            lastRevive = now;
            break;
        }
    }

    @Override public void resetState() { lastRevive = 0L; }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("revive-aura.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ReviveAura.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        String rv = p.getProperty("range");
        if (rv != null) try { range = Math.max(1.0, Math.min(10.0, Double.parseDouble(rv.trim()))); } catch (Exception ignored) {}
        String iv = p.getProperty("intervalMs");
        if (iv != null) try { intervalMs = Math.max(50.0, Math.min(1000.0, Double.parseDouble(iv.trim()))); } catch (Exception ignored) {}
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("range", Double.toString(range));
        p.setProperty("intervalMs", Double.toString(intervalMs));
        try { AtomicProperties.store(FabricRuntime.configPath().resolve("revive-aura.properties"), p, "MICx ReviveAura"); } catch (IOException e) { MicxFabric.LOGGER.warn("Unable to save ReviveAura configuration", e); }
    }
}

package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public final class ZombieFadeModule implements Module {
    private static final ZombieFadeModule INSTANCE = new ZombieFadeModule();
    private boolean enabled;
    private boolean configLoaded;

    private ZombieFadeModule() {}

    public static ZombieFadeModule instance() { return INSTANCE; }

    @Override public String id() { return "zombie_fade"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
    }

    public double getRadius() { loadConfig(); return ZombieFadeRules.radiusBlocks(); }
    public void setRadius(double r) { ZombieFadeRules.setRadiusBlocks(r); saveConfig(); }

    public boolean shouldFade(LivingEntity entity) {
        if (!enabled || entity == null) return false;
        if (!(entity instanceof Enemy)) return false;
        if (!entity.isAlive() || entity.isDeadOrDying()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        double dx = mc.player.getX() - entity.getX();
        double dz = mc.player.getZ() - entity.getZ();
        return ZombieFadeRules.shouldFade(true, false, 0) && ZombieFadeRules.isWithinRadiusSq(dx * dx + dz * dz);
    }

    public static int fadedTint(int originalTint) {
        if (!INSTANCE.enabled) return originalTint;
        int rgb = originalTint & 0x00FFFFFF;
        int a = Math.round(ZombieFadeRules.alpha() * 255f);
        if (a < 0) a = 0; if (a > 255) a = 255;
        return (a << 24) | rgb;
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("zombie-fade.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ZombieFade.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        String v = p.getProperty("radiusBlocks");
        if (v == null) v = p.getProperty("zombie_fade.radiusBlocks");
        if (v != null) try { ZombieFadeRules.setRadiusBlocks(Double.parseDouble(v.trim())); } catch (Exception ignored) {}
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("radiusBlocks", Double.toString(ZombieFadeRules.radiusBlocks()));
        try { AtomicProperties.store(FabricRuntime.configPath().resolve("zombie-fade.properties"), p, "MICx ZombieFade"); } catch (IOException e) { MicxFabric.LOGGER.warn("Unable to save ZombieFade configuration", e); }
    }

    @Override public void resetState() {}
}

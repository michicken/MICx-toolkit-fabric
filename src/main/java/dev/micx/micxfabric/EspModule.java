package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Box ESP migrated to the 26.2 level submit pipeline. */
public final class EspModule implements Module {
    private static final EspModule INSTANCE = new EspModule();
    private boolean enabled;
    private boolean active = true;
    private float range = 64.0f;
    private float alpha = 0.30f;
    private boolean autoGate = true;
    private int gateRound = 60;
    private int gateLowMobs = 20;
    private boolean configLoaded;

    private EspModule() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    public static EspModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "esp";
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
        ModuleStateStore.put(id(), enabled);
    }

    public boolean active() { return active; }
    public float range() { loadConfig(); return range; }
    public float alpha() { loadConfig(); return alpha; }
    public boolean autoGate() { loadConfig(); return autoGate; }
    public int gateRound() { loadConfig(); return gateRound; }
    public int gateLowMobs() { loadConfig(); return gateLowMobs; }

    public void setActive(boolean value) { active = value; saveConfig(); }
    public void setRange(float value) { range = clamp(value, 8.0f, 256.0f); saveConfig(); }
    public void setAlpha(float value) { alpha = clamp(value, 0.05f, 1.0f); saveConfig(); }
    public void setAutoGate(boolean value) { autoGate = value; saveConfig(); }
    public void setGateRound(int value) { gateRound = clamp(value, 10, 110); saveConfig(); }
    public void setGateLowMobs(int value) { gateLowMobs = clamp(value, 1, 100); saveConfig(); }

    private void collectSubmits(LevelRenderContext context) {
        if (!enabled || !active) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;
        ZombiesTracker tracker = ZombiesTracker.instance();
        boolean hideNormal = autoGate && tracker.round() >= gateRound
                && (tracker.zombiesLeft() < 0 || tracker.zombiesLeft() >= gateLowMobs);

        SubmitNodeCollector collector = context.submitNodeCollector();
        var pose = context.poseStack();
        var camera = client.gameRenderer.mainCamera().position();
        var eye = client.player.getEyePosition(1.0f);
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isTarget(living)) continue;
            if (living.isDeadOrDying() || living == client.player) continue;

            boolean priority = living instanceof Giant;
            if (hideNormal && !priority) continue;
            AABB box = living.getBoundingBox();
            double distanceSq = eye.distanceToSqr(box.getCenter());
            if (!priority && (distanceSq < 0.25 || distanceSq > range * range)) continue;

            AABB local = box.move(-living.getX(), -living.getY(), -living.getZ());
            int color = color(living, alpha, client);
            pose.pushPose();
            pose.translate(living.getX() - camera.x, living.getY() - camera.y, living.getZ() - camera.z);
            collector.submitShapeOutline(pose, Shapes.create(local), EspRenderTypes.espLines(), color, 2.0f, false);
            pose.popPose();
        }
    }

    private static boolean isTarget(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof Villager || entity instanceof WitherBoss) return false;
        return entity instanceof Enemy || entity instanceof Wolf || entity instanceof IronGolem;
    }

    /**
     * ESP 颜色（用户定稿 2026-09-11）：巨人<b>无论如何都画框</b>（豁免 autoGate/距离，
     * 见 collectSubmits）；颜色按 ZE badheadshot 分类 —— 巨人已纳入 ZE spawn-order
     * 序列（ZeSpawnOrderTracker.isBadHsAwareMob 含 Giant），被分类为 badheadshot
     * （高处、非最新锚怪）→ 绿色；否则红色。其余怪保持红色。
     */
    private int color(LivingEntity entity, float alpha, Minecraft client) {
        int rgb;
        if (entity instanceof Giant) {
            boolean badHeadshot = client.player != null && ZombiesExplorerModule.instance()
                    .isBadHeadshot(entity, client.player.getY());
            rgb = badHeadshot ? 0x00FF00 : 0xFF3333;
        } else {
            rgb = 0xFF3333;
        }
        int a = Math.max(1, Math.min(255, Math.round(alpha * 255.0f)));
        return (a << 24) | rgb;
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("esp.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ESP.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        active = ConfigProperties.bool(properties, "active", true);
        range = parseFloat(properties.getProperty("range"), 64.0f, 8.0f, 256.0f);
        alpha = parseFloat(properties.getProperty("alphaPct"), 30.0f, 5.0f, 100.0f) / 100.0f;
        autoGate = ConfigProperties.bool(properties, "autoGate", true);
        gateRound = ConfigProperties.integer(properties, "gateRound", 60, 10, 110);
        gateLowMobs = ConfigProperties.integer(properties, "gateLowMobs", 20, 1, 100);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("active", Boolean.toString(active));
        properties.setProperty("range", Float.toString(range));
        properties.setProperty("alphaPct", Float.toString(alpha * 100.0f));
        properties.setProperty("autoGate", Boolean.toString(autoGate));
        properties.setProperty("gateRound", Integer.toString(gateRound));
        properties.setProperty("gateLowMobs", Integer.toString(gateLowMobs));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("esp.properties"), properties,
                    "MICx ESP configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save ESP configuration", exception);
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float parseFloat(String value, float fallback, float min, float max) {
        try { return clamp(Float.parseFloat(value), min, max); }
        catch (Exception ignored) { return fallback; }
    }
}

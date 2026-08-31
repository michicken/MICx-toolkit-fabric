package dev.micx.micxfabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** ZE 1.7 port: Powerup/BadHeadshot markers — box + nameTag (no full-model tint). */
public final class ZombiesExplorerModule implements Module {
    private static final ZombiesExplorerModule INSTANCE = new ZombiesExplorerModule();

    // Colors (26.2 ARGB): predict light red, ensured dark red, badhs green, derived yellow
    private static final int C_PREDICT = 0xFFFF8080;
    private static final int C_ENSURED = 0x9A800000;
    private static final int C_BADHS = 0xFF00FF00;
    private static final int C_DERIVED = 0xFFFFFF00;

    private static boolean powerupDetector = true;
    private static boolean badHeadShotDetector = true;
    private static boolean badHeadShotOnLine = true;
    private static boolean nameTag = true;
    private static int powerupPredictor = 1; // 0-3
    private static boolean enabled;
    private static boolean configLoaded;

    public final ZeSpawnOrderTracker tracker = new ZeSpawnOrderTracker();

    private ZombiesExplorerModule() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    public static ZombiesExplorerModule instance() { return INSTANCE; }

    static boolean powerupDetectorEnabled() { return enabled && powerupDetector; }
    static boolean badHeadShotEnabled() { return enabled && badHeadShotDetector; }
    static int predictorCount() { loadStatic(); return powerupPredictor; }

    @Override public String id() { return "zombies_explorer"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) { loadConfig(); enabled = v; ModuleStateStore.put(id(), v); }

    public boolean getPowerupDetector() { loadConfig(); return powerupDetector; }
    public void setPowerupDetector(boolean v) { powerupDetector = v; saveConfig(); }
    public boolean getBadHeadShotDetector() { loadConfig(); return badHeadShotDetector; }
    public void setBadHeadShotDetector(boolean v) { badHeadShotDetector = v; saveConfig(); }
    public boolean getBadHeadShotOnLine() { loadConfig(); return badHeadShotOnLine; }
    public void setBadHeadShotOnLine(boolean v) { badHeadShotOnLine = v; saveConfig(); }
    public boolean getNameTag() { loadConfig(); return nameTag; }
    public void setNameTag(boolean v) { nameTag = v; saveConfig(); }
    public int getPowerupPredictor() { loadConfig(); return powerupPredictor; }
    public void setPowerupPredictor(int v) { powerupPredictor = Math.max(0, Math.min(3, v)); saveConfig(); }

    // Called from MixinClientPacketListener.handleAddEntity
    public void onEntityJoin(Entity entity) {
        if (!enabled || !(entity instanceof LivingEntity le2)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        if (!ZombiesTracker.instance().isInZombies()) return;
        int round = ZombiesTracker.instance().round();
        WaveTable.ZbMap map = null;
        try {
            String t = ZombiesTracker.instance().frame().map();
            if (t != null) map = WaveTable.detect(t);
            if (map == null) { String st = ZombiesTracker.instance().sidebarTitle(); if (st != null) map = WaveTable.detect(st); }
        } catch (Throwable ignored) {}
        tracker.onEntityJoin(le2, round, map, powerupDetector, powerupPredictor, badHeadShotDetector);
    }

    public void onRoundChanged(int round) {
        tracker.resetRound();
    }

    public void onSessionReset() {
        tracker.resetSession();
    }

    public void onEntitiesRemoved(int[] ids) { tracker.onEntitiesRemoved(ids); }

    void onPowerupActivated(String kind, int round, long elapsedMs) {
        WaveTable.ZbMap map = null;
        try {
            String t = ZombiesTracker.instance().frame().map();
            if (t != null) map = WaveTable.detect(t);
        } catch (Throwable ignored) {}
        String k = kind == null ? "" : kind.toLowerCase(java.util.Locale.ROOT);
        if (k.contains("insta")) tracker.puRounds.latchInsta(round, elapsedMs, map);
        else if (k.contains("max")) tracker.puRounds.latchMax(round, elapsedMs, map);
        else if (k.contains("shopping") || k.equals("ss")) tracker.puRounds.latchSs(round, elapsedMs);
    }

    private void collectSubmits(LevelRenderContext ctx) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        if (!ZombiesTracker.instance().isInZombies()) return;
        // Tick cleanup (idempotent per frame — cheap)
        tracker.tickCleanup();
        if (!powerupDetector && !badHeadShotDetector) return;

        SubmitNodeCollector collector = ctx.submitNodeCollector();
        PoseStack pose = ctx.poseStack();
        Vec3 cam = mc.gameRenderer.mainCamera().position();

        // Build classification with priority: predict > ensured > badhs anchor > derived
        Set<LivingEntity> predictSet = new HashSet<>(tracker.powerupPredict);
        Set<LivingEntity> ensuredSet = new HashSet<>(tracker.powerupEnsured);
        LivingEntity anchor = tracker.badHeadshotAnchor();
        // Derived: entities on line (computed below)
        List<Entity> derived = computeDerived(mc);

        // Render boxes — isRemoved is the authoritative despawn signal (26.2 death animation keeps health>0 briefly)
        for (LivingEntity e : tracker.allEntities) {
            if (e == null || e.isRemoved() || e.isDeadOrDying() || e.getHealth() <= 0) continue;
            int color = classifyColor(e, predictSet, ensuredSet, anchor, derived);
            if (color == 0) continue;
            AABB box = e.getBoundingBox();
            AABB local = box.move(-e.getX(), -e.getY(), -e.getZ());
            pose.pushPose();
            pose.translate(e.getX() - cam.x, e.getY() - cam.y, e.getZ() - cam.z);
            collector.submitShapeOutline(pose, Shapes.create(local), EspRenderTypes.espLines(), color, 2.0f, false);
            pose.popPose();
        }
    }

    private int classifyColor(LivingEntity e, Set<LivingEntity> predict, Set<LivingEntity> ensured,
                              LivingEntity anchor, List<Entity> derived) {
        if (powerupDetector && predict.contains(e)) return C_PREDICT;
        if (powerupDetector && ensured.contains(e)) return C_ENSURED;
        if (badHeadShotDetector && anchor != null && anchor.equals(e)) return C_BADHS;
        if (badHeadShotDetector && badHeadShotOnLine && derived.contains(e)) return C_DERIVED;
        return 0;
    }

    private List<Entity> computeDerived(Minecraft mc) {
        if (!badHeadShotOnLine || !badHeadShotDetector) return List.of();
        LivingEntity anchor = tracker.badHeadshotAnchor();
        if (anchor == null || anchor.isDeadOrDying()) return List.of();
        if (mc.player == null) return List.of();
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getLookAngle();
        Vec3 end = eye.add(look.scale(70));
        // Quick check: anchor must be on line
        if (!isOnLine(anchor, eye, end, mc.player)) return List.of();
        List<Entity> result = new ArrayList<>();
        for (LivingEntity e : tracker.allEntities) {
            if (e == null || e.isRemoved() || e.isDeadOrDying()) continue;
            if (isOnLine(e, eye, end, mc.player)) result.add(e);
        }
        return result;
    }

    private static boolean isOnLine(LivingEntity e, Vec3 eye, Vec3 end, net.minecraft.world.entity.player.Player player) {
        AABB box = e.getBoundingBox().inflate(0.4, 0.1, 0.4);
        var hit = box.clip(eye, end);
        if (hit.isEmpty()) return false;
        // hasLineOfSight check (same as ZE MixinEntityRenderer)
        try {
            if (!player.hasLineOfSight(e)) return false;
        } catch (Throwable ignored) {}
        return true;
    }

    private static void loadStatic() {
        if (configLoaded) return;
        // Trigger instance load
        INSTANCE.loadConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("zombies-explorer.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ZombiesExplorer.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        powerupDetector = ConfigProperties.bool(p, "powerupDetector", true);
        badHeadShotDetector = ConfigProperties.bool(p, "badHeadShotDetector", true);
        badHeadShotOnLine = ConfigProperties.bool(p, "badHeadShotOnLine", true);
        nameTag = ConfigProperties.bool(p, "nameTag", true);
        powerupPredictor = ConfigProperties.integer(p, "powerupPredictor", 1, 0, 3);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("powerupDetector", Boolean.toString(powerupDetector));
        p.setProperty("badHeadShotDetector", Boolean.toString(badHeadShotDetector));
        p.setProperty("badHeadShotOnLine", Boolean.toString(badHeadShotOnLine));
        p.setProperty("nameTag", Boolean.toString(nameTag));
        p.setProperty("powerupPredictor", Integer.toString(powerupPredictor));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("zombies-explorer.properties"), p, "MICx ZombiesExplorer configuration");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save ZombiesExplorer configuration", e);
        }
    }
}

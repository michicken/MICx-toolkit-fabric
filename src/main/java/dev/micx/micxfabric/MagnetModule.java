package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Magnet 吸附（Forge MagnetModule 移植 v1）：按住右键时准心靠近怪物会轻微吸向
 * 目标瞄准点（眼睛高度/爆头点），完全不屏蔽鼠标输入。
 *
 * <p>与 Forge 版差异：26.2 Fabric 无 RenderTickEvent（渲染帧开始事件），
 * 牵引修正改在 END_CLIENT_TICK 应用（约 20Hz；鼠标位移仍由原版每帧照常叠加，
 * 玩家控制权 100% 保留，只是修正粒度从每帧降为每 tick）。减速带 slowMode 完整移植：
 * hitbox 弦深渐变（真身 AABB + AimLead 幽灵框重测）+ 快甩脱困。
 */
public final class MagnetModule implements Module {
    private static final MagnetModule INSTANCE = new MagnetModule();
    /** 吸附扫描的最远距离（格）：超出此距离角度再小也不吸。 */
    private static final double MAX_DIST = 64.0;
    private static final double MOUSE_SLOW_DEG_S = 15.0;
    private static final double MOUSE_FAST_DEG_S = 120.0;
    /** 减速带满粘性弦深（格）：v1 角度路径恒用满减速。 */
    private static final double STICKY_FULL_DEPTH = 0.5;
    /** 近身威胁距离（格，威胁优先选目标，与 Forge 同口径）。 */
    private static final double THREAT_DIST = 6.0;
    /** 牵引收敛率换算：pullStrength(0.15) × 12 → 1.8/s 指数收敛（先快后慢）。 */
    private static final double MAGNET_RATE_SCALE = 12.0;
    /** tick 端纠偏：单 tick 最大修正角（°），防 dt 跳变猛拉。 */
    private static final double MAX_STEP_PER_TICK_DEG = 4.0;

    private boolean enabled;
    private boolean configLoaded;
    private double radiusDeg = 8.0;
    private double pullStrength = 0.15;
    private double headshotStopDeg = 1.5;
    private double slowFactor = 0.5;
    /** 减速带模式：开=靠近目标时降低灵敏度不主动拉；关=叠加牵引（与 Forge pull/slow 互斥同构）。 */
    private boolean slowMode = false;
    /** 减速带触发：仅准心射线命中怪物 hitbox 才减速（弦深渐变）；关=按半径角距满减速。 */
    private boolean hitboxOnly = true;
    private boolean includeSlime = false;
    private boolean includeGolem = false;
    private boolean includeGiant = true;
    private boolean zombiesOnly = true;

    private long lastTickNs = 0L;
    private float lastYawApplied;
    private float lastPitchApplied;
    /** 减速带灵敏度状态：true = 已压低待还原。 */
    private boolean sensModified = false;
    private float sensOrig = 0.5f;

    private MagnetModule() {
    }

    public static MagnetModule instance() {
        return INSTANCE;
    }

    @Override public String id() { return "magnet"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
        if (!v) restoreSensitivity();
        if (v) lastTickNs = 0L;
    }

    public double getRadiusDeg() { loadConfig(); return radiusDeg; }
    public void setRadiusDeg(double v) { radiusDeg = Math.max(2.0, Math.min(30.0, v)); saveConfig(); }
    public double getPullStrength() { loadConfig(); return pullStrength; }
    public void setPullStrength(double v) { pullStrength = Math.max(0.02, Math.min(0.6, v)); saveConfig(); }
    public double getSlowFactor() { loadConfig(); return slowFactor; }
    public void setSlowFactor(double v) { slowFactor = Math.max(0.1, Math.min(0.95, v)); saveConfig(); }
    public boolean isSlowMode() { loadConfig(); return slowMode; }
    public void setSlowMode(boolean v) { slowMode = v; saveConfig(); }
    public boolean isHitboxOnly() { loadConfig(); return hitboxOnly; }
    public void setHitboxOnly(boolean v) { hitboxOnly = v; saveConfig(); }
    public boolean isZombiesOnly() { loadConfig(); return zombiesOnly; }
    public void setZombiesOnly(boolean v) { zombiesOnly = v; saveConfig(); }
    public boolean isIncludeSlime() { loadConfig(); return includeSlime; }
    public void setIncludeSlime(boolean v) { includeSlime = v; saveConfig(); }
    public boolean isIncludeGolem() { loadConfig(); return includeGolem; }
    public void setIncludeGolem(boolean v) { includeGolem = v; saveConfig(); }
    public boolean isIncludeGiant() { loadConfig(); return includeGiant; }
    public void setIncludeGiant(boolean v) { includeGiant = v; saveConfig(); }
    public double getHeadshotStopDeg() { loadConfig(); return headshotStopDeg; }
    public void setHeadshotStopDeg(double v) { headshotStopDeg = Math.max(0.5, Math.min(10.0, v)); saveConfig(); }

    @Override public void tick(Minecraft mc) {
        if (!enabled || mc == null || mc.player == null || mc.level == null) {
            restoreSensitivity();
            return;
        }
        if (!isActiveHere(mc)) {
            restoreSensitivity();
            lastTickNs = 0L;
            syncAppliedView(mc);
            return;
        }
        loadConfig();
        long nowNs = System.nanoTime();
        boolean firstFrame = lastTickNs == 0;
        double dtSec = firstFrame ? 0.05 : Math.min((nowNs - lastTickNs) / 1e9, 0.25);
        lastTickNs = nowNs;

        // 鼠标速度推断（°/s，帧/帧间视角变化，不读 Mouse 不吞位移）
        double mouseSpeed = 0.0;
        if (!firstFrame) {
            mouseSpeed = (Math.abs(mc.player.getYRot() - lastYawApplied)
                    + Math.abs(mc.player.getXRot() - lastPitchApplied)) / Math.max(dtSec, 0.001);
        }

        // ---- 减速带：不拉准心，只降灵敏度；快甩脱困（≥120°/s 减速全解） ----
        if (slowMode) {
            double slowApplied = MagnetRules.slowEscapeFactor(slowFactor, mouseSpeed,
                    MOUSE_SLOW_DEG_S, MOUSE_FAST_DEG_S);
            if (hitboxOnly) {
                HitboxHit hit = pickHitboxTarget(mc);
                if (hit != null) applySlow(mc, hit.depth(), slowApplied);
                else restoreSensitivity();
            } else {
                LivingEntity t = pickTarget(mc);
                if (t != null && slowAngleWithin(mc, t)) {
                    applySlow(mc, STICKY_FULL_DEPTH, slowApplied);   // 旧行为：半径角距内满减速
                } else {
                    restoreSensitivity();
                }
            }
            syncAppliedView(mc);
            return;
        }

        LivingEntity target = pickTarget(mc);
        if (target == null) {
            restoreSensitivity();
            syncAppliedView(mc);
            return;
        }

        // ---- 叠加牵引（动态强度 + 拟人化指数收敛） ----
        restoreSensitivity();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 aim = target.getEyePosition();
        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4) {
            syncAppliedView(mc);
            return;
        }
        double yawDiff = MagnetRules.yawToTarget(dx / len, dz / len, mc.player.getYRot());
        double pitchDiff = MagnetRules.pitchToTarget(dy / len, mc.player.getXRot());
        // 停拉范围动态化：贴脸松/远处紧（"锁范围不锁点"）
        double stopDeg = MagnetRules.headshotStopRange(Math.sqrt(dx * dx + dz * dz), headshotStopDeg);
        if (MagnetRules.withinHeadshotRange(yawDiff, pitchDiff, stopDeg)) {
            syncAppliedView(mc);
            return;
        }
        double factor = MagnetRules.speedFactor(mouseSpeed, MOUSE_SLOW_DEG_S, MOUSE_FAST_DEG_S);
        if (factor <= 0.0) {
            syncAppliedView(mc);   // 手瞄快速移动中：牵引完全退场
            return;
        }
        double rate = pullStrength * factor * MAGNET_RATE_SCALE;
        double corrYaw = MagnetRules.humanStep(yawDiff, rate, dtSec);
        double corrPitch = MagnetRules.humanStep(pitchDiff, rate, dtSec);
        corrYaw = MagnetRules.axisCorrection(yawDiff, Math.min(Math.abs(corrYaw), MAX_STEP_PER_TICK_DEG));
        corrPitch = MagnetRules.axisCorrection(pitchDiff, Math.min(Math.abs(corrPitch), MAX_STEP_PER_TICK_DEG));
        if (corrYaw == 0.0 && corrPitch == 0.0) {
            syncAppliedView(mc);
            return;
        }
        mc.player.turn((float) corrYaw, (float) corrPitch);
        mc.player.setXRot(Math.max(-90f, Math.min(90f, mc.player.getXRot())));
        syncAppliedView(mc);
    }

    /** 门控：存活、无 GUI、Zombies 图（可配）、按住右键。 */
    private boolean isActiveHere(Minecraft mc) {
        if (!mc.player.isAlive()) return false;
        if (mc.gui.screen() != null || mc.isPaused()) return false;
        if (zombiesOnly && !ZombiesTracker.instance().isInZombies()) return false;
        return mc.options.keyUse != null && mc.options.keyUse.isDown();
    }

    /**
     * 吸附半径内选与准心总角距最小的目标；近身威胁（水平距离 ≤ THREAT_DIST）保送优先。
     * 过滤：Monster（僵尸/巨人/小丑等）+ Slime/Golem 按开关；排除玩家/已死/
     * 负实体 ID（机制实体）/超出 MAX_DIST。
     */
    private LivingEntity pickTarget(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        double maxDistSq = MAX_DIST * MAX_DIST;
        LivingEntity best = null;
        double bestTotal = Double.MAX_VALUE;
        LivingEntity bestThreat = null;
        double bestThreatTotal = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive() || living.getId() < 0) continue;
            if (living instanceof Player) continue;
            String typePath = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getPath();
            boolean isSlime = "slime".equals(typePath) || "magma_cube".equals(typePath);
            boolean isGolem = "iron_golem".equals(typePath);
            boolean isGiant = "giant".equals(typePath);
            if (!(living instanceof Monster) && !isSlime && !isGolem) continue;
            if (!MagnetRules.acceptsEntityType(isSlime, isGolem, isGiant,
                    includeSlime, includeGolem, includeGiant)) continue;
            if (living.distanceToSqr(mc.player) > maxDistSq) continue;
            Vec3 body = living.getEyePosition();
            double bdx = body.x - eye.x;
            double bdy = body.y - eye.y;
            double bdz = body.z - eye.z;
            double blen = Math.sqrt(bdx * bdx + bdy * bdy + bdz * bdz);
            if (blen < 1.0E-4) continue;
            double bodyYaw = MagnetRules.yawToTarget(bdx / blen, bdz / blen, mc.player.getYRot());
            double bodyPitch = MagnetRules.pitchToTarget(bdy / blen, mc.player.getXRot());
            double bodyTotal = MagnetRules.totalAngle(bodyYaw, bodyPitch);
            // 性能粗筛（真身角度超「半径+幽灵余量」的目标不精算）
            double bodyHoriz = Math.sqrt(bdx * bdx + bdz * bdz);
            if (bodyTotal > radiusDeg + Math.toDegrees(MagnetRules.ghostSlackRad(bodyHoriz))) continue;
            double yawDiff = MagnetRules.yawToTarget(bdx / blen, bdz / blen, mc.player.getYRot());
            double pitchDiff = MagnetRules.pitchToTarget(bdy / blen, mc.player.getXRot());
            double total = MagnetRules.totalAngle(yawDiff, pitchDiff);
            if (!MagnetRules.withinRadius(total, radiusDeg)) continue;
            double horiz = Math.sqrt(bdx * bdx + bdz * bdz);
            if (horiz <= THREAT_DIST) {
                if (total < bestThreatTotal) {
                    bestThreatTotal = total;
                    bestThreat = living;
                }
            } else if (total < bestTotal) {
                bestTotal = total;
                best = living;
            }
        }
        return bestThreat != null ? bestThreat : best;
    }

    private void syncAppliedView(Minecraft mc) {
        lastYawApplied = mc.player.getYRot();
        lastPitchApplied = mc.player.getXRot();
    }

    /* ---- 减速带灵敏度 ---- */

    /** hitbox 命中结果：目标 + 弦深（射线穿过体积的深度，格）。 */
    private record HitboxHit(LivingEntity target, double depth) {
    }

    /**
     * 准心射线命中怪物 hitbox：真身 AABB 粗筛后用 AimLead 预测框（幽灵框）重测，
     * 命中且更近则覆盖（与 Forge 同构）；无预测时降级为仅真身 AABB。
     */
    private HitboxHit pickHitboxTarget(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);
        double maxDistSq = MAX_DIST * MAX_DIST;
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        double bestDepth = 0.0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive() || living.getId() < 0) continue;
            if (living instanceof Player) continue;
            String typePath = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getPath();
            boolean isSlime = "slime".equals(typePath) || "magma_cube".equals(typePath);
            boolean isGolem = "iron_golem".equals(typePath);
            boolean isGiant = "giant".equals(typePath);
            if (!(living instanceof Monster) && !isSlime && !isGolem) continue;
            if (!MagnetRules.acceptsEntityType(isSlime, isGolem, isGiant,
                    includeSlime, includeGolem, includeGiant)) continue;
            if (living.distanceToSqr(mc.player) > maxDistSq) continue;
            net.minecraft.world.phys.AABB box = living.getBoundingBox();
            double t = MagnetRules.rayIntersectsAabb(eye.x, eye.y, eye.z,
                    look.x, look.y, look.z,
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            if (t >= 0.0 && t < bestT) {
                bestT = t;
                best = living;
                bestDepth = MagnetRules.rayChordDepth(eye.x, eye.y, eye.z,
                        look.x, look.y, look.z,
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            }

            // 幽灵框 AABB 优先：有预测 → 用预测框重测，命中且更近则覆盖
            net.minecraft.world.phys.AABB ghostBox = AimLeadModule.instance().leadBoxFor(living);
            if (ghostBox != null) {
                double gt = MagnetRules.rayIntersectsAabb(eye.x, eye.y, eye.z,
                        look.x, look.y, look.z,
                        ghostBox.minX, ghostBox.minY, ghostBox.minZ,
                        ghostBox.maxX, ghostBox.maxY, ghostBox.maxZ);
                if (gt >= 0.0 && gt < bestT) {
                    bestT = gt;
                    best = living;
                    bestDepth = MagnetRules.rayChordDepth(eye.x, eye.y, eye.z,
                            look.x, look.y, look.z,
                            ghostBox.minX, ghostBox.minY, ghostBox.minZ,
                            ghostBox.maxX, ghostBox.maxY, ghostBox.maxZ);
                }
            }
        }
        return best == null ? null : new HitboxHit(best, bestDepth);
    }

    /** 半径角距减速触发：瞄准点与准心的总角距在吸附半径内。 */
    private boolean slowAngleWithin(Minecraft mc, LivingEntity target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 aim = target.getEyePosition();
        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4) return false;
        double total = MagnetRules.totalAngle(
                MagnetRules.yawToTarget(dx / len, dz / len, mc.player.getYRot()),
                MagnetRules.pitchToTarget(dy / len, mc.player.getXRot()));
        return MagnetRules.shouldSlow(total, radiusDeg);
    }

    private void applySlow(Minecraft mc, double chordDepth, double slowApplied) {
        if (!sensModified) {
            sensOrig = mc.options.sensitivity().get().floatValue();
            sensModified = true;
        }
        // 粘性渐变：弦深越深（对准中心）减速越强，擦边几乎不减速（跟枪不卡）；
        // slowApplied 已含鼠标速度逃逸（快甩 → 接近 1.0，不减速）
        double sticky = MagnetRules.stickyFactor(chordDepth, STICKY_FULL_DEPTH);
        mc.options.sensitivity().set((double) MagnetRules.slowedSensitivity(sensOrig, slowApplied, sticky));
    }

    private void restoreSensitivity() {
        if (!sensModified) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.sensitivity().set((double) sensOrig);
        }
        sensModified = false;
    }

    @Override public void resetInput() {
        restoreSensitivity();
        lastTickNs = 0L;
    }

    @Override public void resetState() {
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("magnet.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_Magnet.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        radiusDeg = clamp(ConfigProperties.real(p, "radiusDeg", 8.0, 2.0, 30.0), 2.0, 30.0);
        pullStrength = clamp(ConfigProperties.real(p, "pullStrength", 0.15, 0.02, 0.6), 0.02, 0.6);
        headshotStopDeg = clamp(ConfigProperties.real(p, "headshotStopDeg", 1.5, 0.5, 10.0), 0.5, 10.0);
        slowFactor = clamp(ConfigProperties.real(p, "slowFactor", 0.5, 0.1, 0.95), 0.1, 0.95);
        slowMode = ConfigProperties.bool(p, "slowMode", false);
        hitboxOnly = ConfigProperties.bool(p, "hitboxOnly", true);
        includeSlime = ConfigProperties.bool(p, "includeSlime", false);
        includeGolem = ConfigProperties.bool(p, "includeGolem", false);
        includeGiant = ConfigProperties.bool(p, "includeGiant", true);
        zombiesOnly = ConfigProperties.bool(p, "zombiesOnly", true);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("radiusDeg", Double.toString(radiusDeg));
        p.setProperty("pullStrength", Double.toString(pullStrength));
        p.setProperty("headshotStopDeg", Double.toString(headshotStopDeg));
        p.setProperty("slowFactor", Double.toString(slowFactor));
        p.setProperty("slowMode", Boolean.toString(slowMode));
        p.setProperty("hitboxOnly", Boolean.toString(hitboxOnly));
        p.setProperty("includeSlime", Boolean.toString(includeSlime));
        p.setProperty("includeGolem", Boolean.toString(includeGolem));
        p.setProperty("includeGiant", Boolean.toString(includeGiant));
        p.setProperty("zombiesOnly", Boolean.toString(zombiesOnly));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("magnet.properties"), p, "MICx Magnet");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save Magnet configuration", e);
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

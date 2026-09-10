package dev.micx.micxfabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 1.8.9 Aimbot selection/lock pipeline ported to the 26.2 Fabric client.
 *
 * <p>The old module's important contract is preserved: it is off by default,
 * only takes over while its gate is active, selects a visible AimLead ghost
 * point, and writes the local view rotation without constructing interaction
 * packets.</p>
 */
public final class AimbotModule implements Module {
    private static final AimbotModule INSTANCE = new AimbotModule();

    /** MouseHandler hook result: keep vanilla mouse processing untouched. */
    public static final int MOUSE_PASS_THROUGH = 0;
    /** MouseHandler hook result: consume both axes because this path owns them. */
    public static final int MOUSE_CONSUME = 1;
    /** MouseHandler hook result: vanilla yaw is allowed, manual pitch is filtered. */
    public static final int MOUSE_LOCK_PITCH = 2;

    private static final int MAX_CANDIDATES = 6;
    private static final double RAY_EXTEND = 420.0;
    private static final long THREAT_TTL_MS = 5_000L;
    private static final double JOY_SWITCH_CONE = 45.0;
    private static final double JOY_YAW_CLAMP = 120.0;
    private static final double JOY_PITCH_CLAMP = 60.0;
    private static final long JOY_SWITCH_COOLDOWN_MS = 400L;
    private static final double JOY_OFF_DECAY = 0.9;
    private static final double CLOSEST_SWITCH_MARGIN = 3.0;

    /* Humanize constants ported from the final 1.8.9 Aimbot implementation. */
    private static final double HUMAN_SLOW_PEAK = 2.0;
    private static final double HUMAN_NOISE = 0.6;
    private static final int FACE_UP_HOLD_TICKS = 8;
    private static final double FACE_UP_RECOVERY_PEAK_DEG = 8.0;
    private static final double FACE_UP_RECOVERY_EPSILON_DEG = 2.0;
    private static final long FACE_UP_RECOVERY_TIMEOUT_MS = 1_000L;
    private static final long FACE_UP_PITCH_HOLD_MS = 1_500L;
    private static final double FACE_UP_PITCH_RELEASE_YAW_DEG = 5.0;
    private static final double YAW_FULL_GAIN_DIST = 3.0;
    private static final double NEAR_SWEEP_LOCK_RADIUS_DEG = 3.0;
    /** 近处怪堆不启用大幅扫描线；近距离改为微摆加逐只点射。 */
    private static final double NEAR_SWEEP_SCAN_MIN_HD = 8.0;
    /** 追踪阶段最大修正范围，超过后重新甩枪，避免近处被目标拖着大幅摆头。 */
    private static final double TRACKING_CORRECTION_CAP_DEG = 12.0;
    /** 手动 A/D 横移时的目标切换滞回，避免同级目标在准心两侧来回抢锁。 */
    private static final double STRAFE_TARGET_SWITCH_MARGIN_DEG = 2.0;
    /** BRUTE 扫射的空间推进容差：signed yaw 必须比上一个目标大出这个量才认作「下一个」。 */
    private static final double BRUTE_SWEEP_ADVANCE_EPS_DEG = 0.25;
    /**
     * AA 地面层 y≈72（实测 71~75）。高于此值才算"尚未落地"；
     * 窗户下落的怪贴地生成（y≤74 且只掉 1~2 格），会被这条排除。
     */
    private static final double MID_GROUND_Y = 76.0;
    /** mid 坠怪判定的水平速度上限（格/tick）：被击退/打飞的怪水平位移明显，不能忽略。 */
    private static final double MID_FALL_MAX_HORIZONTAL = 0.5;
    private static final long STRAFE_SWEEP_GRACE_MS = 150L;
    private static final double TREMOR_YAW_DEG = 0.1;
    private static final double TREMOR_PITCH_DEG = 0.05;
    private static final double AR1_ALPHA = 0.8;
    private static final double AR1_SIGMA_YAW = 0.02;
    private static final double AR1_SIGMA_PITCH = 0.01;
    private static final double PURSUIT_MIN_DEG_PER_SEC = 30.0;
    private static final double PURSUIT_EXIT_DEG_PER_SEC = 18.0;
    private static final double PURSUIT_NOISE_YAW = 0.15;
    private static final double PURSUIT_NOISE_PITCH = 0.1;
    private static final double TRACKING_MAX_VEL_CHANGE = 6.0;
    private static final double TRACKING_MAX_ACCEL = 2.0;
    private static final double PURSUIT_LOST_MULT = 1.5;
    /** The selection/controller path is client-tick based; the actual camera write is per frame. */
    private static final double CONTROLLER_TICK_SECONDS = 1.0 / 20.0;
    /** Do not turn a render hitch into an abnormally large single rotation. */
    private static final double MAX_RENDER_ROTATION_SECONDS = 0.10;

    private final AimbotConfig config = new AimbotConfig();
    private final Map<Integer, Long> threatUntil = new HashMap<>();
    private boolean enabled;
    private int lockedTargetId = -1;
    private Vec3 lastLockedDir;
    private Object lastLevel;

    /* BRUTE 扫射状态：在限定 FOV 内从左到右逐个精准锁定。 */
    private int bruteSweepHeldId = -1;
    private double bruteSweepPrevYawOff = Double.NaN;
    private long bruteSweepNextHopAtMs;

    private double joyYawOff;
    private double joyPitchOff;
    private boolean flickActive;
    private boolean forceReacquire;
    private final int[] flickBelowAcc = new int[1];
    private long lastMouseNs;
    private long joyCooldownUntil;

    private final Random humanRand = new Random();
    private double humanVelYaw;
    private double humanVelPitch;
    private boolean humanTracking;
    private double humanErrYaw;
    private double humanErrPitch;
    /** 当前帧的爆头带垂直容差（°）；<=0 表示无暴击带可守（瞄准点已降级到身体）。 */
    private double critPitchTolDeg;
    private boolean faceUpOn;
    private final int[] faceUpExit = new int[1];
    private boolean faceUpRecovery;
    private double faceUpRestorePitch;
    private long faceUpRecoveryStartedAtMs;
    private boolean faceUpPitchHold;
    private long faceUpPitchHoldUntilMs;
    private double smoothYawTarget = Double.NaN;
    private boolean swingDirty = true;
    private double swingStartDeg;
    private double swingPeakDeg;
    private double swingOvershootDeg;
    private double swingArcDeg;
    private int errHoldTicks;
    private boolean corrActive;
    private long nearSweepNextAtMs;
    private boolean killSwitchPending;
    private int switchStage;
    private long stageUntilMs;
    private double stageMidYaw;
    private double prevTargetYaw = Double.NaN;
    private double prevTargetPitch = Double.NaN;
    private long prevTargetAtMs;
    private boolean pursuitActive;

    /*
     * The controller produces a delta per game tick. Applying that whole
     * delta from ClientTickEvents makes the view move at 20 Hz, which is a
     * visible staircase at any normal frame rate. Keep the decision cadence,
     * but consume its delta continuously from the render path just like zbc9's
     * MatrixStack + tickDelta event does.
     *
     * BRUTE speeds this up by shortening the consumption window (see
     * double bruteRotationWindowSeconds) and by capping each frame's write to
     * the still-unconsumed remainder, so a window shorter than one controller
     * tick can never overshoot the decided delta.
     */
    private double frameYawPerTick;
    private double framePitchPerTick;
    /** Still-unconsumed part of the queued delta; prevents overshoot on short windows. */
    private double frameYawRemaining;
    private double framePitchRemaining;
    /** Seconds over which the queued delta is fully consumed. */
    private double frameRotationWindowSeconds = CONTROLLER_TICK_SECONDS;
    private boolean frameRotationActive;
    private long lastRotationFrameNs;

    private volatile Vec3 debugAim;
    private volatile long debugAimAt;

    private AimbotModule() {
        LevelRenderEvents.START_MAIN.register(this::applyFrameRotation);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectDebugLine);
    }

    public static AimbotModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "aimbot";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        config.load();
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (!enabled) resetState();
        ModuleStateStore.put(id(), enabled);
    }

    /** Main Aimbot toggle binding; empty by default, matching the Forge panel. */
    @Override
    public int[] primaryChord() {
        return config.getToggleKeyCodes();
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (!newlyEnabled) setEnabled(false);
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice(
                    "Aimbot " + (enabled ? "已开启（按住右键时接管视角）" : "已关闭")));
        }
    }

    @Override
    public void resetInput() {
        clearLock();
        clearFrameRotation();
        joyYawOff = 0.0;
        joyPitchOff = 0.0;
        flickActive = false;
        forceReacquire = false;
        flickBelowAcc[0] = 0;
        lastMouseNs = 0L;
        joyCooldownUntil = 0L;
    }

    @Override
    public void resetState() {
        resetInput();
        threatUntil.clear();
        lastLevel = null;
    }

    /** Clears only Humanize's transient controller state, preserving settings and target memory. */
    private void resetHumanState() {
        humanVelYaw = 0.0;
        humanVelPitch = 0.0;
        humanTracking = false;
        humanErrYaw = 0.0;
        humanErrPitch = 0.0;
        faceUpOn = false;
        faceUpExit[0] = 0;
        faceUpRecovery = false;
        faceUpRestorePitch = 0.0;
        faceUpRecoveryStartedAtMs = 0L;
        faceUpPitchHold = false;
        faceUpPitchHoldUntilMs = 0L;
        smoothYawTarget = Double.NaN;
        swingDirty = true;
        swingStartDeg = 0.0;
        swingPeakDeg = 0.0;
        swingOvershootDeg = 0.0;
        swingArcDeg = 0.0;
        errHoldTicks = 0;
        corrActive = false;
        nearSweepNextAtMs = 0L;
        killSwitchPending = false;
        switchStage = 0;
        stageUntilMs = 0L;
        stageMidYaw = 0.0;
        prevTargetYaw = Double.NaN;
        prevTargetPitch = Double.NaN;
        prevTargetAtMs = 0L;
        pursuitActive = false;
    }

    public AimbotConfig config() {
        config.load();
        return config;
    }

    /**
     * AimLead must keep sampling when Aimbot explicitly targets all worlds,
     * even if AimLead's own HUD filter is limited to Zombies sessions.
     */
    boolean needsAimLeadTracking() {
        config.load();
        return enabled && config.aimLead && !config.zombiesOnly;
    }

    public void saveConfiguration() {
        config.save();
    }

    public int hudOffsetX() {
        return config().aimbotHudDx;
    }

    public int hudOffsetY() {
        return config().aimbotHudDy;
    }

    public void setHudOffsets(int x, int y) {
        AimbotConfig c = config();
        c.aimbotHudDx = clamp(x, -2_000, 2_000);
        c.aimbotHudDy = clamp(y, -2_000, 2_000);
    }

    public int lockedTargetId() {
        return lockedTargetId;
    }

    public LivingEntity lockedTargetEntity() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || lockedTargetId < 0) return null;
        Entity entity = client.level.getEntity(lockedTargetId);
        return entity instanceof LivingEntity living && isAliveTarget(living) ? living : null;
    }

    public boolean isJoystickLocked() {
        return enabled && config().joystick && lockedTargetId >= 0;
    }

    public double joyYawOff() {
        return joyYawOff;
    }

    public double joyPitchOff() {
        return joyPitchOff;
    }

    public boolean joystickFlickActive() {
        return flickActive;
    }

    /** Records the attacker behind a client-side damage event for the 1.8.9 threat TTL. */
    public static void recordThreat(LivingEntity attacker) {
        if (!INSTANCE.enabled || attacker == null || !isTarget(attacker) || isChildWolf(attacker)) return;
        INSTANCE.threatUntil.put(attacker.getId(), System.currentTimeMillis() + THREAT_TTL_MS);
    }

    @Override
    public void tick(Minecraft client) {
        config.load();
        if (!enabled || client == null || client.level == null || client.player == null) {
            return;
        }
        boolean bruteMode = config.bruteMode && !config.joystick;
        if (lastLevel != client.level) {
            lastLevel = client.level;
            clearLock();
            threatUntil.clear();
            forceReacquire = false;
        }
        long now = System.currentTimeMillis();
        removeExpiredThreats(now);
        if (!isActiveHere(client)) {
            resetActiveGate();
            return;
        }
        if (!config.joystick) {
            joyYawOff = 0.0;
            joyPitchOff = 0.0;
            flickActive = false;
            forceReacquire = false;
            flickBelowAcc[0] = 0;
            lastMouseNs = 0L;
            joyCooldownUntil = 0L;
        }
        if (!config.humanize || config.joystick || bruteMode) resetHumanState();
        if (flickActive) {
            clearLock();
            return;
        }

        Vec3 eye = client.player.getEyePosition(1.0f);
        Vec3 look = client.player.getViewVector(1.0f);
        List<CandidateMeta> all = collectCandidates(client, eye, look, now);
        if (all.isEmpty()) {
            clearLock();
            return;
        }
        all.sort(Comparator.comparingDouble(meta -> meta.angle));

        List<CandidateMeta> scanList = buildScanList(all);
        List<Scored> scored = new ArrayList<>(scanList.size());
        for (CandidateMeta meta : scanList) {
            Scored result = scanTarget(client, meta, eye, all);
            if (result != null) scored.add(result);
        }
        if (scored.isEmpty()) {
            clearLock();
            return;
        }

        Scored current = findLocked(scored);
        boolean joystickSwitching = config.joystick
                && AimbotRules.isJoystickSwitching(joyYawOff, joyPitchOff, config.joystickSwitchDeg)
                && now >= joyCooldownUntil;
        scored.sort((a, b) -> compareScored(a, b, eye, joystickSwitching));

        Scored best;
        if (forceReacquire) {
            best = scored.stream().min(Comparator.comparingDouble(s -> s.angle)).orElse(scored.get(0));
            forceReacquire = false;
        } else if (joystickSwitching) {
            best = joystickChoice(client, eye, scored, current);
        } else if (AimbotRules.joystickHoldCurrent(config.joystick, false, current != null)) {
            best = current;
        } else if (bruteMode) {
            best = bruteChoice(client, eye, scored, now);
        } else if (config.humanize) {
            best = humanizedChoice(scored, current, isManualStrafing(client));
        } else {
            best = stickyChoice(scored.get(0), current);
        }

        int previous = lockedTargetId;
        lockedTargetId = best.entity.getId();
        lastLockedDir = direction(eye, best.point);
        if (previous != lockedTargetId && config.joystick) {
            joyYawOff = 0.0;
            joyPitchOff = 0.0;
            joyCooldownUntil = now + JOY_SWITCH_COOLDOWN_MS;
        }
        if (previous != lockedTargetId && config.humanize && !config.joystick && !bruteMode) {
            onHumanTargetChanged(client, previous, now);
        }
        debugAim = best.point;
        debugAimAt = now;
        boolean suppressFire = config.humanize && !config.joystick && !bruteMode
                && (killSwitchPending || switchStage != 0);
        if (bruteMode) {
            applyBruteView(client, best);
        } else if (config.humanize && !config.joystick) {
            applyHumanizedView(client, best, scored, now);
            suppressFire |= killSwitchPending || switchStage != 0 || faceUpRecovery;
        } else if (!config.joystick) {
            // Normal/Giant pitch policy still needs a 20 Hz controller when
            // Humanize is disabled; mouse input remains free for normal mobs
            // and Giants, while yaw continues to follow the selected target.
            applyNonHumanizedView(client, best);
        }
        if (config.holdLock && !suppressFire) fireUseKey(client);
    }

    private boolean isActiveHere(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return false;
        if (!client.player.isAlive() || client.player.isDeadOrDying()
                || client.player.getHealth() <= 0.0f) return false;
        if (client.isPaused() || client.gui == null || client.gui.screen() != null) return false;
        if (config.zombiesOnly && !ZombiesTracker.instance().isInZombies()) return false;
        if (config.holdLock) return KeyChord.isAllDown(config.getHoldLockKeyCodes(), client);
        return !config.onlyFire || client.options == null || client.options.keyUse == null
                || client.options.keyUse.isDown();
    }

    private void resetActiveGate() {
        clearLock();
        joyYawOff = 0.0;
        joyPitchOff = 0.0;
        flickActive = false;
        forceReacquire = false;
        flickBelowAcc[0] = 0;
        lastMouseNs = 0L;
        joyCooldownUntil = 0L;
    }

    private void clearLock() {
        lockedTargetId = -1;
        lastLockedDir = null;
        debugAim = null;
        debugAimAt = 0L;
        resetHumanState();
        resetBruteSweep();
        clearFrameRotation();
    }

    private void removeExpiredThreats(long now) {
        Iterator<Map.Entry<Integer, Long>> iterator = threatUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) iterator.remove();
        }
    }

    private List<CandidateMeta> collectCandidates(Minecraft client, Vec3 eye, Vec3 look, long now) {
        AimbotConfig c = config;
        List<CandidateMeta> result = new ArrayList<>();
        double maxDistSq = (double) c.maxDist * c.maxDist;
        double fovHalf = c.fov >= 360 ? 180.0 : c.fov * 0.5;
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isAliveTarget(living)
                    || living == client.player || living.getId() < 0) continue;
            if (living instanceof Player || living instanceof WitherBoss) continue;
            if (!AimLeadModule.isTarget(living)) continue;
            if (isChildWolf(living)) continue;

            boolean too = isToo(living);
            boolean giant = isGiant(living);
            boolean slime = isSlime(living);
            boolean golem = living instanceof IronGolem;
            if (c.ignoreToo && too) continue;
            if (c.ignoreGolem && golem) continue;
            if (c.ignoreSlime && slime) continue;
            if (c.ignoreVerticalFall && isVerticalFalling(living)) continue;
            if (c.ignoreMidFall && isMidFallDrop(living)) continue;
            if (c.ignoreAbovePlayer && !isGhast(living)
                    && AimbotRules.isTooHighAbove(living.getY(), py, c.aboveHeightBlocks)) continue;
            double dx = living.getX() - px;
            double dy = living.getY() - client.player.getY();
            double dz = living.getZ() - pz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDistSq) continue;

            double horizontal = Math.hypot(dx, dz);
            Long until = threatUntil.get(living.getId());
            boolean threat = c.threatEnabled && AimbotRules.isThreat(horizontal, c.threatDist,
                    until == null ? 0L : until, now);
            double angle = angleTo(eye, look, living.getX(),
                    living.getY() + living.getBbHeight() * 0.5, living.getZ());
            if (!threat && c.fov < 360 && Math.toDegrees(angle) > fovHalf) continue;

            boolean baby = living instanceof Zombie zombie && zombie.isBaby() && !too;
            boolean clown = living instanceof Zombie zombie && isClown(zombie);
            int group = AimbotRules.groupRank(c.prioBaby, c.prioClown, c.prioGiant,
                    baby, clown, giant);
            result.add(new CandidateMeta(living, threat, too, giant, group, angle,
                    Math.sqrt(distSq)));
        }
        return result;
    }

    private List<CandidateMeta> buildScanList(List<CandidateMeta> all) {
        int topGroup = 0;
        for (CandidateMeta meta : all) topGroup = Math.max(topGroup, meta.group);
        List<CandidateMeta> list = new ArrayList<>(MAX_CANDIDATES);
        if (topGroup > 0) {
            for (CandidateMeta meta : all) {
                if (list.size() >= MAX_CANDIDATES) break;
                if (meta.group == topGroup) list.add(meta);
            }
        }
        for (CandidateMeta meta : all) {
            if (list.size() >= MAX_CANDIDATES) break;
            if (!list.contains(meta) && meta.threat) list.add(meta);
        }
        for (CandidateMeta meta : all) {
            if (list.size() >= MAX_CANDIDATES) break;
            if (!list.contains(meta)) list.add(meta);
        }
        return list;
    }

    private Scored scanTarget(Minecraft client, CandidateMeta meta, Vec3 eye,
                              List<CandidateMeta> all) {
        if (!config.aimLead) return null;
        AABB box = leadBox(meta.entity);
        if (box == null) return null;
        double height = box.maxY - box.minY;
        Vec3 executablePoint = computeAimPoint(client, meta.entity);
        boolean allowNormalBodyFallback = !meta.giant && !isBadHeadshot(client, meta.entity);
        Vec3 fallbackPoint = null;
        for (int percent = 95; percent >= 0; percent -= 5) {
            if (!isGiant(meta.entity) && percent <= 80 && percent % 10 != 0) continue;
            double y = box.minY + height * percent / 100.0;
            Vec3 scanPoint = new Vec3((box.minX + box.maxX) * 0.5, y,
                    (box.minZ + box.maxZ) * 0.5);
            if (!canWallShot(client, eye, scanPoint)) continue;
            if (allowNormalBodyFallback && fallbackPoint == null) fallbackPoint = scanPoint;
            if (executablePoint != null) {
                return scoredPoint(client, meta, eye, all, box, executablePoint);
            }
        }
        // The preferred head/critical point may be behind a block or outside
        // the usable pitch window. Keep the first visible sample so a normal
        // body hit remains possible instead of tracking empty air.
        return fallbackPoint == null
                ? null
                : scoredPoint(client, meta, eye, all, box, fallbackPoint);
    }

    private Scored scoredPoint(Minecraft client, CandidateMeta meta, Vec3 eye,
                               List<CandidateMeta> all, AABB box, Vec3 point) {
        double height = box.maxY - box.minY;
        boolean headLine = AimbotRules.isHeadLayer(point.y, box.minY, height);
        int penetration = countPenetration(eye, point, meta.entity, all);
        AimbotRules.Candidate candidate = new AimbotRules.Candidate(meta.threat, meta.too,
                meta.giant, headLine, penetration, meta.angle);
        return new Scored(meta.entity, candidate, point, meta.group,
                meta.distance, meta.angle);
    }

    private Scored stickyChoice(Scored best, Scored current) {
        if (current == null || best.group > current.group) return best;
        boolean swap = AimbotRules.shouldSwitch(current.candidate, best.candidate, earlyRound());
        if (!swap && config.closest) swap = AimbotRules.closestBetter(
                best.distance, current.distance, CLOSEST_SWITCH_MARGIN);
        return swap ? best : current;
    }

    /**
     * BRUTE 选靶。默认取排序第一（排序已把 TOO/巨人放最前，再按转向角最小）。
     * 暴力扫射生效时改为「逐个精准锁定 + 超快速切换」：把限定 FOV 内的目标按相对准星的
     * signed yaw 从左到右排好，依次停留并完整瞄准每一个，停留 dwellMs 后再跳下一个；
     * 扫到最右端重新回到最左。不做连续扫描线，也不做 360° 乱扫。
     */
    private Scored bruteChoice(Minecraft client, Vec3 eye, List<Scored> scored, long now) {
        if (!bruteSweepActive()) {
            resetBruteSweep();
            return scored.get(0);
        }
        float currentYaw = client.player.getYRot();
        int size = scored.size();
        double[] yawOff = new double[size];
        int[] slot = new int[size];
        int count = 0;
        for (int i = 0; i < size; i++) {
            float[] angles = calculateYawPitch(eye, scored.get(i).point);
            double off = AimbotRules.angleDelta(currentYaw, angles[0]);
            if (!AimbotRules.bruteSweepInFov(off, config.bruteSweepFovDeg)) continue;
            int k = count++;
            while (k > 0 && yawOff[k - 1] > off) {
                yawOff[k] = yawOff[k - 1];
                slot[k] = slot[k - 1];
                k--;
            }
            yawOff[k] = off;
            slot[k] = i;
        }
        if (count == 0) {
            resetBruteSweep();
            return scored.get(0);
        }
        if (bruteSweepHeldId >= 0 && now < bruteSweepNextHopAtMs) {
            for (int k = 0; k < count; k++) {
                Scored value = scored.get(slot[k]);
                if (value.entity.getId() == bruteSweepHeldId) return value;
            }
        }
        int pos = AimbotRules.bruteSweepAdvance(yawOff, bruteSweepPrevYawOff,
                BRUTE_SWEEP_ADVANCE_EPS_DEG);
        if (pos < 0) pos = 0;
        Scored pick = scored.get(slot[pos]);
        bruteSweepHeldId = pick.entity.getId();
        bruteSweepPrevYawOff = yawOff[pos];
        bruteSweepNextHopAtMs = now + Math.max(40, config.bruteSweepDwellMs);
        return pick;
    }

    /** 暴力扫射是否生效：开关开启 + Zombies 局内 + 已达到起始回合（回合未知不门控）。 */
    private boolean bruteSweepActive() {
        if (!config.bruteSweep) return false;
        if (!ZombiesTracker.instance().isInZombies()) return false;
        int round = ZombiesTracker.instance().round();
        return round <= 0 || AimbotRules.bruteSweepAllowed(round, config.bruteSweepMinRound);
    }

    private void resetBruteSweep() {
        bruteSweepHeldId = -1;
        bruteSweepPrevYawOff = Double.NaN;
        bruteSweepNextHopAtMs = 0L;
    }

    /** Humanized target choice: sweep the crosshair cone instead of pinning one entity forever. */
    private Scored humanizedChoice(List<Scored> scored) {
        if (sweepSuppressedByRound()) return scored.get(0);
        Scored sweep = null;
        double[] angleDeg = new double[scored.size()];
        for (int i = 0; i < scored.size(); i++) {
            Scored value = scored.get(i);
            angleDeg[i] = Math.toDegrees(value.angle);
            if (angleDeg[i] > AimbotRules.SWEEP_CONE_DEG) continue;
            if (sweep == null || AimbotRules.compareSweepCandidates(
                    angleDeg[i], Math.toDegrees(sweep.angle)) < 0) {
                sweep = value;
            }
        }
        if (sweep != null) return sweep;
        int front = AimbotRules.frontWindowPickIdx(angleDeg,
                AimbotRules.SWEEP_FRONT_WINDOW_DEG);
        return front >= 0 ? scored.get(front) : scored.get(0);
    }

    /**
     * While the player is manually strafing, keep a valid same-priority target
     * unless the replacement is clearly closer to the crosshair. This prevents
     * two static mobs on opposite sides from making the view alternate left/right.
     */
    private Scored humanizedChoice(List<Scored> scored, Scored current, boolean manualStrafing) {
        Scored choice = humanizedChoice(scored);
        if (!manualStrafing || current == null
                || current.entity.getId() == choice.entity.getId()) return choice;
        if (choice.group > current.group
                || AimbotRules.shouldSwitch(current.candidate, choice.candidate, earlyRound())) {
            return choice;
        }
        if (current.group > choice.group) {
            return current;
        }

        double currentAngle = Math.toDegrees(current.angle);
        double choiceAngle = Math.toDegrees(choice.angle);
        if (currentAngle <= AimbotRules.SWEEP_CONE_DEG + STRAFE_TARGET_SWITCH_MARGIN_DEG
                && choiceAngle + STRAFE_TARGET_SWITCH_MARGIN_DEG >= currentAngle) {
            return current;
        }
        return choice;
    }

    private boolean isManualStrafing(Minecraft client) {
        if (client == null || client.options == null) return false;
        return (client.options.keyLeft != null && client.options.keyLeft.isDown())
                || (client.options.keyRight != null && client.options.keyRight.isDown());
    }

    private void onHumanTargetChanged(Minecraft client, int previousId, long now) {
        boolean wasFaceUp = faceUpOn || faceUpRecovery;
        double restorePitch = faceUpRestorePitch;
        boolean previousAlive = false;
        if (previousId >= 0 && client.level != null) {
            Entity previous = client.level.getEntity(previousId);
            previousAlive = previous instanceof LivingEntity living && isAliveTarget(living);
        }
        if (previousId < 0) {
            resetHumanState();
        } else {
            // A live-target sweep keeps velocity/offset continuity; a dead-target
            // handoff starts a fresh saccade after the human reaction pause.
            humanTracking = false;
            pursuitActive = false;
            errHoldTicks = 0;
            corrActive = false;
            smoothYawTarget = client.player.getYRot();
            swingDirty = true;
            switchStage = 0;
            stageUntilMs = 0L;
            prevTargetYaw = Double.NaN;
            prevTargetPitch = Double.NaN;
            prevTargetAtMs = 0L;
            faceUpOn = false;
            faceUpExit[0] = 0;
            faceUpPitchHold = false;
            if (wasFaceUp) {
                // A target handoff while the old target was point-blank must
                // still recover from the sky view before the new yaw turn.
                faceUpRecovery = true;
                faceUpRestorePitch = restorePitch;
                faceUpRecoveryStartedAtMs = now;
            } else {
                faceUpRecovery = false;
                faceUpRestorePitch = 0.0;
                faceUpRecoveryStartedAtMs = 0L;
            }
            killSwitchPending = !previousAlive;
            if (!previousAlive) {
                humanVelYaw = 0.0;
                humanVelPitch = 0.0;
                humanErrYaw = 0.0;
                humanErrPitch = 0.0;
            }
        }
        if (Double.isNaN(smoothYawTarget)) smoothYawTarget = client.player.getYRot();
    }

    private int compareScored(Scored a, Scored b, Vec3 eye, boolean joystickSwitching) {
        if (config.closest && a.candidate.threat != b.candidate.threat) {
            return a.candidate.threat ? -1 : 1;
        }
        if (config.closest) {
            int distance = Double.compare(a.distance, b.distance);
            if (distance != 0) return distance;
        }
        // 用户定稿 2026-09-10：TOO 与巨人优先于转向角（会主动索敌的只有这两类），
        // 威胁相关逻辑保持原样不动；其余仍按「需要转动的角度最小」优先，
        // 保留该口径是为了不被快速发现异常（暴力模式同样留余地）。
        boolean aKey = a.candidate.too || a.candidate.giant;
        boolean bKey = b.candidate.too || b.candidate.giant;
        if (aKey != bKey) return aKey ? -1 : 1;
        int turn = Double.compare(a.candidate.angle, b.candidate.angle);
        if (turn != 0) return turn;
        if (a.group != b.group) return Integer.compare(b.group, a.group);
        if (config.nearestFirst && lastLockedDir != null && !joystickSwitching) {
            Vec3 aDirection = direction(eye, a.point);
            Vec3 bDirection = direction(eye, b.point);
            double aDot = dot(aDirection, lastLockedDir);
            double bDot = dot(bDirection, lastLockedDir);
            if (Double.compare(aDot, bDot) != 0) return Double.compare(bDot, aDot);
        }
        return AimbotRules.compareCandidates(a.candidate, b.candidate);
    }

    private Scored joystickChoice(Minecraft client, Vec3 eye, List<Scored> scored, Scored current) {
        float[] base = current == null
                ? new float[]{client.player.getYRot(), client.player.getXRot()}
                : calculateYawPitch(eye, current.point);
        float[] pushed = AimbotRules.applyJoystickOffset(base[0], base[1], joyYawOff, joyPitchOff);
        Vec3 pushedLook = AimbotRules.lookFromAngles(pushed[0], pushed[1]);
        double[] angles = new double[scored.size()];
        for (int i = 0; i < scored.size(); i++) {
            Scored value = scored.get(i);
            angles[i] = Math.toDegrees(angleTo(eye, pushedLook,
                    value.point.x, value.point.y, value.point.z));
        }
        int pick = AimbotRules.joystickPickIdx(angles, JOY_SWITCH_CONE);
        if (pick >= 0 && (current == null || scored.get(pick).entity.getId() != current.entity.getId())) {
            return scored.get(pick);
        }
        return current == null ? scored.get(0) : current;
    }

    private Scored findLocked(List<Scored> scored) {
        for (Scored value : scored) {
            if (value.entity.getId() == lockedTargetId) return value;
        }
        return null;
    }

    private boolean earlyRound() {
        return config.zombiesOnly && ZombiesTracker.instance().isInZombies()
                && ZombiesTracker.instance().round() > 0
                && ZombiesTracker.instance().round() < 9;
    }

    /** Called from the MouseHandler mixin before vanilla consumes accumulated mouse movement. */
    public int onMouseTurn(double dx, double dy) {
        if (!enabled) return MOUSE_PASS_THROUGH;
        Minecraft client = Minecraft.getInstance();
        if (client == null || !isActiveHere(client)) return MOUSE_PASS_THROUGH;
        config.load();

        if (config.joystick) {
            long now = System.nanoTime();
            double dt = lastMouseNs == 0L ? 0.016 : Math.min(0.25,
                    Math.max(0.001, (now - lastMouseNs) / 1_000_000_000.0));
            lastMouseNs = now;
            double speed = (Math.abs(dx) + Math.abs(dy)) / dt;
            boolean wasFlick = flickActive;
            flickActive = AimbotRules.flickStep(flickActive, speed,
                    config.joystickFlickPxPerSec, config.joystickFlickExitMs,
                    Math.round(dt * 1000.0), flickBelowAcc);
            if (flickActive) {
                if (!wasFlick) {
                    clearLock();
                    joyYawOff = 0.0;
                    joyPitchOff = 0.0;
                }
                return MOUSE_PASS_THROUGH;
            }
            if (wasFlick) {
                forceReacquire = true;
                flickBelowAcc[0] = 0;
                lastMouseNs = 0L;
                return MOUSE_PASS_THROUGH;
            }
            if (Math.abs(dx) > 1.0) joyYawOff = clamp(joyYawOff + dx * config.joystickSensitivity,
                    -JOY_YAW_CLAMP, JOY_YAW_CLAMP);
            if (Math.abs(dy) > 1.0) joyPitchOff = clamp(joyPitchOff + dy * config.joystickSensitivity,
                    -JOY_PITCH_CLAMP, JOY_PITCH_CLAMP);
            joyYawOff = AimbotRules.joyDecay(joyYawOff,
                    Math.pow(JOY_OFF_DECAY, dt * 60.0));
            joyPitchOff = AimbotRules.joyDecay(joyPitchOff,
                    Math.pow(JOY_OFF_DECAY, dt * 60.0));
            if (lockedTargetId < 0) return MOUSE_PASS_THROUGH;
        }

        LivingEntity target = lockedTargetEntity();
        if (target == null) return MOUSE_PASS_THROUGH;
        boolean badHeadshot = isBadHeadshot(client, target);

        if (config.humanize && !config.joystick) {
            // Ordinary mobs and Giants keep the complete vanilla mouse path so
            // the player can freely adjust pitch. BadHeadShot only filters the
            // manual pitch axis; yaw remains usable and the tick controller
            // keeps the strict pitch target.
            return badHeadshot ? MOUSE_LOCK_PITCH : MOUSE_PASS_THROUGH;
        }

        if (!badHeadshot && !config.joystick) {
            // Ordinary mobs/Giants need vanilla mouse input for the unlocked
            // pitch policy. The tick controller handles the aimbot side.
            return MOUSE_PASS_THROUGH;
        }

        Vec3 aim = computeAimPoint(client, target);
        if (aim == null) return badHeadshot ? MOUSE_LOCK_PITCH : MOUSE_PASS_THROUGH;
        if (badHeadshot && !config.joystick) return MOUSE_LOCK_PITCH;
        applyView(client, aim);
        return MOUSE_CONSUME;
    }

    /** Produces one 20 Hz Humanize controller step; the render path applies it continuously. */
    private void applyHumanizedView(Minecraft client, Scored best, List<Scored> scored, long now) {
        AimbotConfig c = config;
        Vec3 eye = client.player.getEyePosition(1.0f);
        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        LivingEntity target = best.entity;
        boolean badHeadshot = isBadHeadshot(client, target);
        boolean giant = isGiant(target);
        boolean manualStrafing = isManualStrafing(client);
        double horizontalDistance = Math.hypot(
                target.getX() - client.player.getX(), target.getZ() - client.player.getZ());

        boolean faceUp = false;
        boolean wasFaceUp = faceUpOn;
        if (!faceUpRecovery) {
            // The old implementation only enabled this for BadHeadShot/Giant.
            // Normal Zombies also need the point-blank sky shot because their
            // XZ direction becomes undefined when they pass through the player.
            boolean faceUpEligible = target instanceof Zombie || badHeadshot || giant;
            faceUp = faceUpEligible && AimbotRules.faceUpStep(faceUpOn,
                    isAliveTarget(target),
                    AimbotRules.faceUpTrigger(horizontalDistance, c.faceUpDist),
                    FACE_UP_HOLD_TICKS, faceUpExit);
            if (!wasFaceUp && faceUp) {
                faceUpRestorePitch = currentPitch;
                faceUpPitchHold = false;
            } else if (wasFaceUp && !faceUp && isAliveTarget(target)) {
                beginFaceUpRecovery(now);
            }
            faceUpOn = faceUp;
        }
        if (manualStrafing && nearSweepNextAtMs < now + STRAFE_SWEEP_GRACE_MS) {
            nearSweepNextAtMs = now + STRAFE_SWEEP_GRACE_MS;
        }
        if (faceUp) {
            // At point-blank range yaw is undefined; keep it still and move only
            // pitch toward the top of the target's hitbox.
            killSwitchPending = false;
            switchStage = 0;
            corrActive = false;
            humanVelYaw = 0.0;
            double pitchTarget = AimbotRules.faceUpPitchTarget(
                    target.getY() + target.getBbHeight(),
                    client.player.getY() + client.player.getEyeHeight(),
                    horizontalDistance);
            double strictPitchDelta = AimbotRules.angleDelta(currentPitch, pitchTarget);
            humanVelPitch = AimbotRules.humanizeStep(
                    strictPitchDelta, humanVelPitch,
                    c.humanizePeakDeg, 1.5 * c.humanizePeakDeg,
                    0.3 * c.humanizePeakDeg, c.humanizeOvershoot);
            writeHumanizedView(client, 0.0, humanVelPitch);
            return;
        }

        if (faceUpRecovery) {
            // Do not let the target's new side determine yaw while the view is
            // coming down from the sky. The next phase turns toward it only
            // after this recovery has completed.
            applyFaceUpRecovery(client, c, now);
            return;
        }

        float[] targetAngles = calculateYawPitch(eye, best.point);
        if (faceUpPitchHold && (now >= faceUpPitchHoldUntilMs
                || Math.abs(AimbotRules.angleDelta(currentYaw, targetAngles[0]))
                <= FACE_UP_PITCH_RELEASE_YAW_DEG)) {
            faceUpPitchHold = false;
        }
        double lockRadius = Math.max(
                AimbotRules.headRadiusDeg(horizontalDistance), c.humanizeMaxErrDeg);
        // 暴击优先：人为 pitch 误差（原本下限 1°、上限 3°）必须夹在爆头带内，否则必然脱靶。
        critPitchTolDeg = critPitchToleranceDeg(client, best.entity, best.point);

        // Two or more targets in the crosshair cone produce a continuous scan
        // across the group instead of pinning one entity on every tick.
        int coneCount = 0;
        double minRelativeYaw = Double.POSITIVE_INFINITY;
        double maxRelativeYaw = Double.NEGATIVE_INFINITY;
        double coneHorizontalDistanceSum = 0.0;
        float conePitch = targetAngles[1];
        double coneRad = Math.toRadians(AimbotRules.SWEEP_CONE_DEG);
        for (Scored value : scored) {
            if (value.angle > coneRad) continue;
            float[] valueAngles = calculateYawPitch(eye, value.point);
            double relativeYaw = AimbotRules.angleDelta(currentYaw, valueAngles[0]);
            if (Math.abs(relativeYaw) > AimbotRules.SWEEP_CONE_DEG) continue;
            coneCount++;
            minRelativeYaw = Math.min(minRelativeYaw, relativeYaw);
            maxRelativeYaw = Math.max(maxRelativeYaw, relativeYaw);
            if (coneCount == 1) conePitch = valueAngles[1];
            coneHorizontalDistanceSum += Math.hypot(
                    value.entity.getX() - client.player.getX(),
                    value.entity.getZ() - client.player.getZ());
        }
        if (!manualStrafing && !sweepSuppressedByRound()
                && AimbotRules.shouldSweepScan(coneCount,
                maxRelativeYaw - minRelativeYaw, AimbotRules.SWEEP_MIN_SPAN_DEG)
                && coneHorizontalDistanceSum / coneCount >= NEAR_SWEEP_SCAN_MIN_HD) {
            killSwitchPending = false;
            switchStage = 0;
            corrActive = false;
            humanTracking = false;
            pursuitActive = false;
            double scanRelativeYaw = AimbotRules.sweepScanAngleDeg(
                    minRelativeYaw, maxRelativeYaw, now / AimbotRules.SWEEP_SCAN_CYCLE_MS);
            double scanYaw = currentYaw + scanRelativeYaw;
            double scanPitch = AimbotRules.angleDelta(currentPitch, conePitch);
            double scanPeak = Math.min(c.humanizePeakDeg, AimbotRules.SWEEP_SCAN_PEAK_DEG);
            humanVelYaw = AimbotRules.humanizeStep(
                    AimbotRules.angleDelta(currentYaw, scanYaw), humanVelYaw,
                    scanPeak, 1.5 * scanPeak, 0.3 * scanPeak, 0.0);
            humanVelPitch = AimbotRules.humanizeStep(
                    scanPitch, humanVelPitch, scanPeak * 0.6,
                    1.5 * scanPeak * 0.6, 0.3 * scanPeak * 0.6, 0.0);
            prevTargetYaw = targetAngles[0];
            prevTargetPitch = targetAngles[1];
            prevTargetAtMs = now;
            double pitchDelta = humanizedPolicyPitchDelta(client, target, best.point,
                    targetAngles[0], targetAngles[1], humanVelPitch, badHeadshot);
            writeHumanizedView(client, humanVelYaw, pitchDelta);
            return;
        }

        if (Double.isNaN(prevTargetYaw)) {
            prevTargetYaw = targetAngles[0];
            prevTargetPitch = targetAngles[1];
            prevTargetAtMs = now;
        }
        double targetYawVelocity = AimbotRules.targetAngularVelocityDegPerSec(
                prevTargetYaw, targetAngles[0], now - prevTargetAtMs);
        double targetPitchVelocity = AimbotRules.targetAngularVelocityDegPerSec(
                prevTargetPitch, targetAngles[1], now - prevTargetAtMs);
        prevTargetYaw = targetAngles[0];
        prevTargetPitch = targetAngles[1];
        prevTargetAtMs = now;

        boolean pursuing = !manualStrafing
                && AimbotRules.pursuitNeededWithHysteresis(
                        pursuitActive, targetYawVelocity, targetPitchVelocity,
                        PURSUIT_MIN_DEG_PER_SEC, PURSUIT_EXIT_DEG_PER_SEC)
                && switchStage == 0 && !killSwitchPending;
        pursuitActive = pursuing;
        if (pursuing) {
            // Smooth pursuit follows a moving target's angular velocity rather
            // than alternating between zero and a large correction pulse.
            humanTracking = false;
            corrActive = false;
            errHoldTicks = 0;
            smoothYawTarget = targetAngles[0] + humanErrYaw;
            humanVelYaw = clampVelocityChange(
                    AimbotRules.pursuitStep(targetYawVelocity, PURSUIT_NOISE_YAW, humanRand),
                    humanVelYaw);
            humanVelPitch = clampVelocityChange(
                    AimbotRules.pursuitStep(targetPitchVelocity, PURSUIT_NOISE_PITCH, humanRand),
                    humanVelPitch);
            double pursuitDistance = Math.hypot(
                    AimbotRules.angleDelta(currentYaw, smoothYawTarget),
                    AimbotRules.angleDelta(currentPitch, targetAngles[1] + humanErrPitch));
            if (pursuitDistance > c.humanizeRepullDeg * PURSUIT_LOST_MULT) {
                humanVelYaw = 0.0;
                humanVelPitch = 0.0;
                swingDirty = true;
            }
            double pitchDelta = humanizedPolicyPitchDelta(client, target, best.point,
                    targetAngles[0], targetAngles[1], humanVelPitch, badHeadshot);
            writeHumanizedView(client, humanVelYaw, pitchDelta);
            return;
        }

        if (killSwitchPending) {
            killSwitchPending = false;
            humanTracking = false;
            humanErrYaw = 0.0;
            humanErrPitch = 0.0;
            humanVelYaw = 0.0;
            humanVelPitch = 0.0;
            corrActive = false;
            if (Math.abs(AimbotRules.angleDelta(currentYaw, targetAngles[0]))
                    > AimbotRules.SWITCH_TWO_STEP_DEG) {
                switchStage = 1;
                stageUntilMs = now + (long) AimbotRules.killPauseMs(humanRand);
            } else {
                switchStage = 0;
                swingDirty = true;
            }
        }

        if (Double.isNaN(smoothYawTarget)) smoothYawTarget = currentYaw;
        double yawDistance = AimbotRules.angleDelta(currentYaw, targetAngles[0] + humanErrYaw);
        double pitchDistance = AimbotRules.angleDelta(currentPitch, targetAngles[1] + humanErrPitch);
        double distance = Math.hypot(yawDistance, pitchDistance);

        if (!humanTracking && distance <= lockRadius) {
            humanTracking = true;
            switchStage = 0;
            killSwitchPending = false;
            humanErrYaw = randomError(Math.min(Math.max(lockRadius * 0.6, 2.5),
                    AimbotRules.NEAR_SWEEP_ERR_CAP_DEG));
            humanErrPitch = randomError(Math.min(Math.max(lockRadius * 0.6, 1.0),
                    AimbotRules.NEAR_SWEEP_ERR_CAP_DEG));
            clampHumanErrPitch();
            errHoldTicks = 0;
            corrActive = false;
            nearSweepNextAtMs = 0L;
            smoothYawTarget = targetAngles[0] + humanErrYaw;
        }

        double outputYaw = 0.0;
        double outputPitch = 0.0;
        if (humanTracking) {
            double rawYawTarget = targetAngles[0] + humanErrYaw;
            double yawGain = manualStrafing
                    ? 1.0
                    : AimbotRules.yawFollowGain(horizontalDistance, YAW_FULL_GAIN_DIST);
            smoothYawTarget += yawGain
                    * AimbotRules.angleDelta(smoothYawTarget, rawYawTarget);
            double trackingYawDistance = AimbotRules.angleDelta(currentYaw, smoothYawTarget);
            double trackingPitchDistance = AimbotRules.angleDelta(
                    currentPitch, targetAngles[1] + humanErrPitch);
            double trackingDistance = Math.hypot(trackingYawDistance, trackingPitchDistance);
            double correctionCap = manualStrafing
                    ? Double.POSITIVE_INFINITY
                    : Math.min(Math.max(c.humanizeRepullDeg, lockRadius * 2.0),
                    TRACKING_CORRECTION_CAP_DEG);

            if (trackingDistance <= lockRadius) {
                if (!manualStrafing && corrActive) {
                    corrActive = false;
                    double settle = AimbotRules.correctionSettle(lockRadius, humanRand);
                    if (settle > 0.0) {
                        humanErrYaw += AimbotRules.tremor(humanRand, settle);
                        humanErrPitch += AimbotRules.tremor(humanRand, settle * 0.5);
                        clampHumanErrPitch();
                        if (settle >= lockRadius * 0.5) {
                            errHoldTicks = AimbotRules.reactionDelayTicks(humanRand);
                        }
                    }
                    humanVelYaw = 0.0;
                    humanVelPitch = 0.0;
                }
                if (!manualStrafing && lockRadius > NEAR_SWEEP_LOCK_RADIUS_DEG) {
                    if (now >= nearSweepNextAtMs) {
                        humanErrYaw = randomError(AimbotRules.nearSweepErrAmplitude(lockRadius));
                        humanErrPitch = randomError(
                                AimbotRules.nearSweepErrAmplitude(lockRadius) * 0.5);
                        clampHumanErrPitch();
                        nearSweepNextAtMs = now + (long) AimbotRules.nearSweepIntervalMs(
                                humanRand, c.humanizeCorrMs);
                        smoothYawTarget = targetAngles[0] + humanErrYaw;
                        humanVelYaw = 0.0;
                        humanVelPitch = 0.0;
                        errHoldTicks = 0;
                    }
                } else if (!manualStrafing) {
                    humanErrYaw += AimbotRules.tremor(humanRand, TREMOR_YAW_DEG);
                    humanErrPitch += AimbotRules.tremor(humanRand, TREMOR_PITCH_DEG);
                    humanErrYaw = AimbotRules.ar1Step(
                            humanErrYaw, AR1_ALPHA, AR1_SIGMA_YAW, humanRand);
                    humanErrPitch = AimbotRules.ar1Step(
                            humanErrPitch, AR1_ALPHA, AR1_SIGMA_PITCH, humanRand);
                    clampHumanErrPitch();
                }
                double correctionYaw = AimbotRules.angleDelta(currentYaw, smoothYawTarget);
                double correctionPitch = AimbotRules.angleDelta(
                        currentPitch, targetAngles[1] + humanErrPitch);
                humanVelYaw = clampVelocityChange(AimbotRules.humanizeStep(
                        correctionYaw, humanVelYaw, HUMAN_SLOW_PEAK,
                        1.5 * HUMAN_SLOW_PEAK, TRACKING_MAX_ACCEL, 0.0), humanVelYaw);
                humanVelPitch = clampVelocityChange(AimbotRules.humanizeStep(
                        correctionPitch, humanVelPitch, HUMAN_SLOW_PEAK,
                        1.5 * HUMAN_SLOW_PEAK, TRACKING_MAX_ACCEL, 0.0), humanVelPitch);
            } else if (trackingDistance <= correctionCap) {
                if (errHoldTicks > 0 && !manualStrafing) {
                    errHoldTicks--;
                    humanVelYaw = 0.0;
                    humanVelPitch = 0.0;
                } else {
                    corrActive = true;
                    humanVelYaw = clampVelocityChange(AimbotRules.humanizeStep(
                            trackingYawDistance, humanVelYaw, HUMAN_SLOW_PEAK,
                            1.5 * HUMAN_SLOW_PEAK, TRACKING_MAX_ACCEL, 0.0), humanVelYaw);
                    humanVelPitch = clampVelocityChange(AimbotRules.humanizeStep(
                            trackingPitchDistance, humanVelPitch, HUMAN_SLOW_PEAK,
                            1.5 * HUMAN_SLOW_PEAK, TRACKING_MAX_ACCEL, 0.0), humanVelPitch);
                }
            } else {
                humanTracking = false;
                humanVelYaw = 0.0;
                humanVelPitch = 0.0;
                corrActive = false;
                swingDirty = true;
            }
        }

        if (!humanTracking) {
            if (switchStage == 1) {
                if (now >= stageUntilMs) {
                    switchStage = 2;
                    stageMidYaw = AimbotRules.headTurnMidAngle(
                            currentYaw, targetAngles[0], AimbotRules.SWITCH_HEAD_TURN_MARGIN_DEG);
                    stageUntilMs = now + (long) AimbotRules.SWITCH_HEAD_TURN_BUDGET_MS;
                } else {
                    humanVelYaw = 0.0;
                    humanVelPitch = 0.0;
                }
            }
            if (switchStage == 2) {
                double middleDistance = AimbotRules.angleDelta(currentYaw, stageMidYaw);
                if (Math.abs(middleDistance) < 2.0 || now >= stageUntilMs) {
                    switchStage = 0;
                    humanVelYaw = 0.0;
                    humanVelPitch = 0.0;
                    swingDirty = true;
                } else {
                    double peak = Math.min(c.humanizePeakDeg,
                            AimbotRules.saccadePeakDegPerTick(180.0));
                    humanVelYaw = AimbotRules.humanizeStep(
                            middleDistance, humanVelYaw, peak, 1.5 * peak, 0.3 * peak, 0.0);
                    humanVelPitch = 0.0;
                }
            } else if (switchStage == 0) {
                if (swingDirty) {
                    swingStartDeg = Math.max(distance, 1.0E-6);
                    swingPeakDeg = Math.min(c.humanizePeakDeg,
                            AimbotRules.saccadePeakDegPerTick(swingStartDeg));
                    double scale = c.humanizeOvershoot / 5.0;
                    swingOvershootDeg = AimbotRules.overshootForSwing(
                            swingStartDeg, AimbotRules.headRadiusDeg(horizontalDistance))
                            * Math.max(0.0, scale);
                    swingArcDeg = Math.min(2.0, swingStartDeg * 0.02)
                            * (0.3 + 0.7 * humanRand.nextDouble());
                    swingDirty = false;
                }
                double progress = 1.0 - Math.min(1.0,
                        distance / Math.max(swingStartDeg, 1.0E-6));
                double arc = AimbotRules.pathArcOffset(progress, swingArcDeg);
                double desiredYaw = AimbotRules.angleDelta(
                        currentYaw, targetAngles[0] + humanErrYaw + arc);
                double desiredPitch = AimbotRules.angleDelta(
                        currentPitch, targetAngles[1] + humanErrPitch);
                humanVelYaw = AimbotRules.humanizeStep(
                        desiredYaw, humanVelYaw, swingPeakDeg, 1.5 * swingPeakDeg,
                        0.3 * swingPeakDeg, swingOvershootDeg);
                humanVelPitch = AimbotRules.humanizeStep(
                        desiredPitch, humanVelPitch, swingPeakDeg, 1.5 * swingPeakDeg,
                        0.3 * swingPeakDeg, swingOvershootDeg);
                double noiseScale = AimbotRules.tremorSuppressionScale(distance, 5.0);
                humanVelYaw = clamp(humanVelYaw
                        + AimbotRules.tremor(humanRand, HUMAN_NOISE * noiseScale),
                        -swingPeakDeg, swingPeakDeg);
                humanVelPitch = clamp(humanVelPitch
                        + AimbotRules.tremor(humanRand, HUMAN_NOISE * noiseScale),
                        -swingPeakDeg, swingPeakDeg);
            }
            outputYaw = humanVelYaw;
            outputPitch = humanVelPitch;
        } else {
            outputYaw = humanVelYaw;
            outputPitch = humanVelPitch;
        }
        outputPitch = humanizedPolicyPitchDelta(client, target, best.point,
                targetAngles[0], targetAngles[1], outputPitch, badHeadshot);
        writeHumanizedView(client, outputYaw, outputPitch);
    }

    private void beginFaceUpRecovery(long now) {
        faceUpOn = false;
        faceUpExit[0] = 0;
        faceUpRecovery = true;
        faceUpRecoveryStartedAtMs = now;
        faceUpPitchHold = false;
        humanVelYaw = 0.0;
        humanVelPitch = 0.0;
        corrActive = false;
        switchStage = 0;
        killSwitchPending = false;
    }

    /** Returns the view to its pre-faceUp pitch before any automatic yaw turn. */
    private void applyFaceUpRecovery(Minecraft client, AimbotConfig c, long now) {
        double pitchDelta = faceUpRestorePitch - client.player.getXRot();
        long elapsed = Math.max(0L, now - faceUpRecoveryStartedAtMs);
        if (Math.abs(pitchDelta) <= FACE_UP_RECOVERY_EPSILON_DEG
                || elapsed >= FACE_UP_RECOVERY_TIMEOUT_MS) {
            faceUpRecovery = false;
            faceUpRecoveryStartedAtMs = 0L;
            faceUpPitchHold = true;
            faceUpPitchHoldUntilMs = now + FACE_UP_PITCH_HOLD_MS;
            humanVelYaw = 0.0;
            humanVelPitch = 0.0;
            writeHumanizedView(client, 0.0, 0.0);
            return;
        }

        humanVelYaw = 0.0;
        double peak = Math.min(Math.max(1.0, c.humanizePeakDeg), FACE_UP_RECOVERY_PEAK_DEG);
        humanVelPitch = AimbotRules.humanizeStep(
                pitchDelta, humanVelPitch, peak, 1.5 * peak, 0.3 * peak, 0.0);
        writeHumanizedView(client, 0.0, humanVelPitch);
    }

    /**
     * Brute style controller: immediate candidate replacement and shortest
     * path rotation, while retaining the target-specific pitch/face-up rules.
     * The resulting delta still goes through the render-frame consumer.
     */
    private void applyBruteView(Minecraft client, Scored best) {
        AimbotConfig c = config;
        Vec3 eye = client.player.getEyePosition(1.0f);
        LivingEntity target = best.entity;
        boolean badHeadshot = isBadHeadshot(client, target);
        boolean giant = isGiant(target);
        double horizontalDistance = Math.hypot(
                target.getX() - client.player.getX(), target.getZ() - client.player.getZ());
        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        float[] targetAngles = calculateYawPitch(eye, best.point);
        boolean faceUpEligible = target instanceof Zombie || badHeadshot || giant;

        if (faceUpEligible && AimbotRules.faceUpTrigger(horizontalDistance, c.faceUpDist)) {
            double pitchTarget = AimbotRules.faceUpPitchTarget(
                    target.getY() + target.getBbHeight(),
                    client.player.getY() + client.player.getEyeHeight(),
                    horizontalDistance);
            queueRotationDelta(0.0, AimbotRules.bruteRotationStep(
                    pitchTarget - currentPitch, c.bruteMaxDegPerTick), bruteRotationWindowSeconds());
            return;
        }

        double yawDelta = AimbotRules.bruteRotationStep(
                AimbotRules.angleDelta(currentYaw, targetAngles[0]), c.bruteMaxDegPerTick);
        double pitchDelta;
        if (badHeadshot) {
            pitchDelta = AimbotRules.bruteRotationStep(
                    targetAngles[1] - currentPitch, c.bruteMaxDegPerTick);
        } else {
            double preferred = normalPitchTarget(client, best.entity, best.point,
                    targetAngles[0], targetAngles[1]);
            pitchDelta = pitchAlreadyOnTarget(client, best.entity, best.point,
                    currentPitch, targetAngles[1])
                    ? 0.0
                    : AimbotRules.bruteRotationStep(
                    preferred - currentPitch, c.bruteMaxDegPerTick);
        }
        queueRotationDelta(yawDelta, pitchDelta, bruteRotationWindowSeconds());
    }

    /** Non-humanized 20 Hz decision controller with the same vertical policy. */
    private void applyNonHumanizedView(Minecraft client, Scored best) {
        Vec3 eye = client.player.getEyePosition(1.0f);
        float[] targetAngles = calculateYawPitch(eye, best.point);
        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        double yawDelta = AimbotRules.smoothRotationStep(
                AimbotRules.angleDelta(currentYaw, targetAngles[0]), config.maxDegPerTick);
        float yaw = currentYaw + (float) yawDelta;

        double pitch;
        if (isBadHeadshot(client, best.entity)) {
            // BadHeadShot is the legacy strict path: no manual pitch window.
            double pitchDelta = AimbotRules.smoothRotationStep(
                    targetAngles[1] - currentPitch, config.maxDegPerTick);
            pitch = currentPitch + pitchDelta;
        } else {
            double preferred = normalPitchTarget(client, best.entity, best.point,
                    targetAngles[0], targetAngles[1]);
            pitch = pitchAlreadyOnTarget(client, best.entity, best.point,
                    currentPitch, targetAngles[1])
                    ? currentPitch
                    : currentPitch + AimbotRules.smoothRotationStep(
                    preferred - currentPitch, config.maxDegPerTick);
        }

        applyViewAngles(client, yaw, (float) clamp(pitch, -90.0, 90.0));
    }

    /** Applies lazy normal pitch, strict BadHeadShot pitch, or Giant up-only pitch. */
    private double humanizedPolicyPitchDelta(Minecraft client, LivingEntity target,
                                              Vec3 aimPoint,
                                              double targetYaw, double targetPitch,
                                              double strictDelta,
                                              boolean badHeadshot) {
        if (badHeadshot) return strictDelta;
        if (faceUpPitchHold) {
            humanVelPitch = 0.0;
            return 0.0;
        }
        float currentPitch = client.player.getXRot();

        // 松锁判据用「真实攻击点」而不是叠加了地平线预留的角度：
        // 否则预留(最多 3°)与容差(默认 2°)会叠加成 5° 误差，准心停在怪物头顶的空气里。
        if (pitchAlreadyOnTarget(client, target, aimPoint, currentPitch, targetPitch)) {
            humanVelPitch = 0.0;
            return 0.0;
        }
        double preferred = normalPitchTarget(client, target, aimPoint,
                (float) targetYaw, targetPitch);
        humanVelPitch = AimbotRules.humanizeStep(
                preferred - currentPitch, humanVelPitch,
                config.humanizePeakDeg, 1.5 * config.humanizePeakDeg,
                0.3 * config.humanizePeakDeg, config.humanizeOvershoot);
        return humanVelPitch;
    }

    /**
     * Normal mobs may reserve a few degrees above the horizon only if the
     * corresponding ray intersects the predicted hitbox and remains visible.
     * Otherwise return the real executable-point pitch and allow a non-crit
     * body hit. BadHeadShot and Giant callers never use this fallback.
     */
    private double normalPitchTarget(Minecraft client, LivingEntity target, Vec3 aimPoint,
                                     float targetYaw, double targetPitch) {
        if (!Double.isFinite(targetPitch)) return 0.0;
        // 预留角度先被"攻击点到箱顶的余量"夹住，保证预留不会把准心推出碰撞箱。
        double margin = Math.min(Math.abs(config.pitchHorizonMarginDeg),
                safeReserveDeg(client, target, aimPoint));
        // 暴击优先：地平线预留不得把准心推出爆头带（>=80% 高度那一段）。
        double crit = critPitchToleranceDeg(client, target, aimPoint);
        if (crit > 0.0) margin = Math.min(margin, crit);
        if (margin <= 0.0) return targetPitch;
        double preferred = AimbotRules.normalPitchTarget(targetPitch, margin);
        if (preferred >= targetPitch) return preferred;
        boolean reachable = pitchRayHitsTarget(client, target, targetYaw, preferred);
        return AimbotRules.normalPitchTargetWithFallback(targetPitch, margin, reachable);
    }

    /**
     * 地平线预留的安全上限：把"攻击点到碰撞箱顶的垂直余量"按水平距离换算成角度，
     * 再打对折留边距。这样远距离小目标（余量 0.4 格 / 20 格 ≈ 1.1°）拿不到 3° 的预留，
     * 近距离大目标才允许完整预留 —— 准心不会因为预留而离开怪物。
     */
    private double safeReserveDeg(Minecraft client, LivingEntity target, Vec3 aimPoint) {
        if (client == null || client.player == null || target == null || aimPoint == null) return 0.0;
        AABB box = leadBox(target);
        if (box == null) box = target.getBoundingBox();
        Vec3 eye = client.player.getEyePosition(1.0f);
        double distance = Math.hypot(target.getX() - eye.x, target.getZ() - eye.z);
        double clearance = box.maxY - aimPoint.y;
        if (clearance <= 0.0 || distance <= 0.05) return 0.0;
        return 0.5 * Math.toDegrees(Math.atan(clearance / distance));
    }

    /**
     * 普通怪的 pitch 松锁：当前 pitch 离真实攻击点在容差内就不与玩家争抢。
     * 不再把「射线打到碰撞箱任意位置」当作已锁定 —— 碰撞箱向下一直延伸到脚底，
     * 该判据对偏低瞄准极其宽容（蹭到腿/身体就冻结），是"经常往下打"的根因。
     */
    private boolean pitchAlreadyOnTarget(Minecraft client, LivingEntity target,
                                         Vec3 aimPoint, double currentPitch,
                                         double targetPitch) {
        return AimbotRules.normalPitchCompatible(currentPitch, targetPitch,
                pitchHoldToleranceDeg(client, target, aimPoint));
    }

    /**
     * pitch 保持容差：瞄准点落在爆头层（>=80% 高度）内时收紧到爆头带，
     * 保证"锁定"时准心确实在暴击区；瞄准点已降级到身体时无暴击带可守，沿用配置值。
     */
    private double pitchHoldToleranceDeg(Minecraft client, LivingEntity target, Vec3 aimPoint) {
        double crit = critPitchToleranceDeg(client, target, aimPoint);
        if (crit <= 0.0) return config.pitchHoldToleranceDeg;
        return Math.min(config.pitchHoldToleranceDeg, crit);
    }

    /** 瞄准点所在爆头带的垂直角度容差（°）；不在爆头层内时返回 0。 */
    private double critPitchToleranceDeg(Minecraft client, LivingEntity target, Vec3 aimPoint) {
        if (client == null || client.player == null || target == null || aimPoint == null) {
            return 0.0;
        }
        Vec3 eye = client.player.getEyePosition(1.0f);
        double distance = Math.hypot(target.getX() - eye.x, target.getZ() - eye.z);
        return AimbotRules.critPitchToleranceDeg(aimPoint.y, target.getY(),
                target.getBbHeight(), distance);
    }

    /** 暴击优先：人为 pitch 误差不得把准心推出爆头带。 */
    private void clampHumanErrPitch() {
        if (critPitchTolDeg <= 0.0) return;
        if (humanErrPitch > critPitchTolDeg) humanErrPitch = critPitchTolDeg;
        else if (humanErrPitch < -critPitchTolDeg) humanErrPitch = -critPitchTolDeg;
    }

    private boolean pitchRayHitsTarget(Minecraft client, LivingEntity target,
                                       float yaw, double pitch) {
        if (client == null || client.player == null || target == null) return false;
        AABB box = leadBox(target);
        if (box == null) return false;
        Vec3 eye = client.player.getEyePosition(1.0f);
        Vec3 direction = AimbotRules.lookFromAngles(yaw, (float) pitch);
        Vec3 end = eye.add(direction.scale(RAY_EXTEND));
        Vec3 hit = box.clip(eye, end).orElse(null);
        return hit != null && canWallShot(client, eye, hit);
    }

    private void writeHumanizedView(Minecraft client, double yawDelta, double pitchDelta) {
        queueRotationDelta(
                AimbotRules.quantizeRotation(yawDelta),
                AimbotRules.quantizeRotation(pitchDelta));
    }

    private double clampVelocityChange(double next, double previous) {
        if (next > previous + TRACKING_MAX_VEL_CHANGE) return previous + TRACKING_MAX_VEL_CHANGE;
        if (next < previous - TRACKING_MAX_VEL_CHANGE) return previous - TRACKING_MAX_VEL_CHANGE;
        return next;
    }

    private double randomError(double maxDeg) {
        return (humanRand.nextDouble() * 2.0 - 1.0) * maxDeg;
    }

    private void applyView(Minecraft client, Vec3 aim) {
        Vec3 eye = client.player.getEyePosition(1.0f);
        float[] angles = calculateYawPitch(eye, aim);
        float yaw = angles[0];
        float pitch = angles[1];
        if (config.bruteMode && !config.joystick) {
            yaw = client.player.getYRot() + (float) AimbotRules.bruteRotationStep(
                    AimbotRules.angleDelta(client.player.getYRot(), yaw),
                    config.bruteMaxDegPerTick);
            pitch = client.player.getXRot() + (float) AimbotRules.bruteRotationStep(
                    pitch - client.player.getXRot(), config.bruteMaxDegPerTick);
        } else if (!config.humanize && !config.joystick) {
            yaw = client.player.getYRot() + (float) AimbotRules.smoothRotationStep(
                    AimbotRules.angleDelta(client.player.getYRot(), yaw), config.maxDegPerTick);
            pitch = client.player.getXRot() + (float) AimbotRules.smoothRotationStep(
                    pitch - client.player.getXRot(), config.maxDegPerTick);
        }
        // This method is currently used by the high-frequency joystick mouse
        // path. Keep it immediate so the joystick retains its own input feel;
        // tick-driven Humanize/non-Humanize paths use queueRotationDelta.
        applyViewAnglesNow(client, yaw, pitch);
    }

    /**
     * Applies a target angle as the shortest current-to-target delta.
     * Keeping this conversion in one place makes the absolute target produced
     * by the selector behave like the zbc9 m51822 rotation path.
     */
    private void applyViewAngles(Minecraft client, float targetYaw, float targetPitch) {
        if (client == null || client.player == null) return;
        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        queueRotationDelta(
                AimbotRules.angleDelta(currentYaw, targetYaw),
                targetPitch - currentPitch);
    }

    /** Immediate variant used only for the mouse-driven joystick path. */
    private void applyViewAnglesNow(Minecraft client, float targetYaw, float targetPitch) {
        if (client == null || client.player == null) return;
        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        applyRotationDeltaNow(client,
                AimbotRules.angleDelta(currentYaw, targetYaw),
                targetPitch - currentPitch);
    }

    /** Stores one controller-tick delta for smooth render-frame consumption. */
    private void queueRotationDelta(double yawDelta, double pitchDelta) {
        queueRotationDelta(yawDelta, pitchDelta, CONTROLLER_TICK_SECONDS);
    }

    /**
     * Same as above but drains the delta over an explicit window. A window
     * shorter than one controller tick makes the camera reach the decided
     * angle sooner, which is what BRUTE wants; the remaining-budget cap in
     * {@link #applyFrameRotation} keeps that from overshooting.
     */
    private void queueRotationDelta(double yawDelta, double pitchDelta, double windowSeconds) {
        if (!Double.isFinite(yawDelta) || !Double.isFinite(pitchDelta)) return;
        frameYawPerTick = yawDelta;
        framePitchPerTick = pitchDelta;
        frameYawRemaining = yawDelta;
        framePitchRemaining = pitchDelta;
        frameRotationWindowSeconds = Double.isFinite(windowSeconds) && windowSeconds > 0.0
                ? windowSeconds
                : CONTROLLER_TICK_SECONDS;
        frameRotationActive = Math.abs(yawDelta) > 1.0E-6 || Math.abs(pitchDelta) > 1.0E-6;
    }

    /** BRUTE 的旋转消耗窗口（秒）；越短转向越快，已在配置层夹到 5–50ms。 */
    private double bruteRotationWindowSeconds() {
        return config.bruteRotationWindowMs / 1000.0;
    }

    /** 把一步写入夹到尚未消耗的余量内，避免短窗口重复消耗同一份转向量。 */
    private static double clampToRemaining(double step, double remaining) {
        return AimbotRules.limitToRemaining(step, remaining);
    }


    private void clearFrameRotation() {
        frameYawPerTick = 0.0;
        framePitchPerTick = 0.0;
        frameYawRemaining = 0.0;
        framePitchRemaining = 0.0;
        frameRotationWindowSeconds = CONTROLLER_TICK_SECONDS;
        frameRotationActive = false;
        lastRotationFrameNs = 0L;
    }

    /**
     * zbc9 runs its m51822 delta application from a MatrixStack/tickDelta
     * render event. This is the Fabric equivalent: a tick controller decides
     * the next 50 ms turn, and every rendered frame consumes only its time
     * share. yRotO/xRotO are still synchronized at every applied delta.
     */
    private void applyFrameRotation(LevelTerrainRenderContext ignored) {
        long now = System.nanoTime();
        long previous = lastRotationFrameNs;
        lastRotationFrameNs = now;
        if (!frameRotationActive || previous == 0L) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || !isActiveHere(client) || config.joystick) {
            clearFrameRotation();
            return;
        }

        double elapsedSeconds = Math.min(MAX_RENDER_ROTATION_SECONDS,
                Math.max(0.0, (now - previous) / 1_000_000_000.0));
        double window = frameRotationWindowSeconds;
        double yawDelta = clampToRemaining(AimbotRules.renderStepFromTickDelta(
                frameYawPerTick, elapsedSeconds, window), frameYawRemaining);
        double pitchDelta = clampToRemaining(AimbotRules.renderStepFromTickDelta(
                framePitchPerTick, elapsedSeconds, window), framePitchRemaining);
        frameYawRemaining -= yawDelta;
        framePitchRemaining -= pitchDelta;
        applyRotationDeltaNow(client, yawDelta, pitchDelta);
    }

    /**
     * zbc9-style rotation application: add the actual delta to the current
     * view and shift yRotO/xRotO by the same applied delta. The previous
     * rotation therefore keeps the render interpolation continuous instead of
     * lagging behind a direct setYRot/setXRot write.
     */
    private void applyRotationDeltaNow(Minecraft client, double yawDelta, double pitchDelta) {
        if (client == null || client.player == null) return;
        if (!Double.isFinite(yawDelta) || !Double.isFinite(pitchDelta)) return;

        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        float nextYaw = currentYaw + (float) yawDelta;
        float nextPitch = (float) clamp(currentPitch + pitchDelta, -90.0, 90.0);
        float appliedYaw = nextYaw - currentYaw;
        float appliedPitch = nextPitch - currentPitch;

        client.player.setYRot(nextYaw);
        client.player.setXRot(nextPitch);
        client.player.yRotO += appliedYaw;
        client.player.xRotO = (float) clamp(client.player.xRotO + appliedPitch, -90.0, 90.0);
    }

    private Vec3 computeAimPoint(Minecraft client, LivingEntity target) {
        if (!config.aimLead || client == null || client.player == null) return null;
        AABB box = leadBox(target);
        if (box == null) return null;
        boolean giant = isGiant(target);
        double frac = !giant && config.insta ? 0.5 : 0.9 + 0.2 * config.crits;
        if (!giant && !config.insta && isBadHeadshot(client, target)) frac = 0.76;
        frac = Math.max(0.05, Math.min(config.headFracMax, frac));
        if (!giant && config.vcrits > 0.0) {
            double lower = Math.min(0.5, config.vcrits * Math.abs(box.minY - client.player.getY()));
            frac = Math.max(0.5, frac * (1.0 - lower));
        }
        Vec3 point = new Vec3((box.minX + box.maxX) * 0.5,
                box.minY + (box.maxY - box.minY) * frac,
                (box.minZ + box.maxZ) * 0.5);
        return canWallShot(client, client.player.getEyePosition(1.0f), point) ? point : null;
    }

    private AABB leadBox(LivingEntity target) {
        return AimLeadModule.instance().leadBoxFor(target);
    }

    private int countPenetration(Vec3 eye, Vec3 point, LivingEntity self, List<CandidateMeta> all) {
        Vec3 delta = point.subtract(eye);
        double length = Math.sqrt(delta.lengthSqr());
        if (length < 1.0E-6) return 0;
        Vec3 end = eye.add(delta.scale(RAY_EXTEND / length));
        int count = 0;
        for (CandidateMeta meta : all) {
            LivingEntity other = meta.entity;
            if (other == self || !isAliveTarget(other)) continue;
            AABB box = other.getBoundingBox();
            if (box.contains(eye) || box.clip(eye, end).isPresent()) count++;
        }
        return count;
    }

    /** 1.8.9 wall policy: the first colliding block decides. */
    private boolean canWallShot(Minecraft client, Vec3 start, Vec3 end) {
        try {
            BlockHitResult hit = client.level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() == HitResult.Type.MISS) return true;
            var state = client.level.getBlockState(hit.getBlockPos());
            if (state.getCollisionShape(client.level, hit.getBlockPos()).isEmpty()) return true;
            var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String path = key == null ? "" : key.getPath();
            if (isAllowedFirstSolid(state, path)) return true;
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    /** Mirrors the old Block/BlockSlab/BlockStairs whitelist on modern registry paths. */
    private boolean isAllowedFirstSolid(BlockState state, String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.endsWith("_slab")) {
            boolean doubleSlab = path.startsWith("double_");
            if (!doubleSlab && state != null && state.getBlock() instanceof SlabBlock) {
                doubleSlab = state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
            }
            return AimbotRules.isAllowedSingleSlab(path, doubleSlab);
        }
        if (path.endsWith("_door") || path.endsWith("_trapdoor")) return true;
        if (path.equals("iron_bars") || path.endsWith("_glass") || path.endsWith("_glass_pane")
                || path.equals("barrier") || path.contains("sign")) return true;
        if (path.endsWith("_fence") || path.endsWith("_fence_gate") || path.endsWith("_wall")) {
            return true;
        }
        if (path.endsWith("_stairs")) {
            if (path.contains("sandstone") || path.equals("nether_brick_stairs")
                    || isWoodStairPath(path)) return false;
            return config.wsStair;
        }
        return false;
    }

    private static boolean isWoodStairPath(String path) {
        return path.endsWith("_stairs") && (path.startsWith("oak_")
                || path.startsWith("spruce_") || path.startsWith("birch_")
                || path.startsWith("jungle_") || path.startsWith("acacia_")
                || path.startsWith("dark_oak_") || path.startsWith("mangrove_")
                || path.startsWith("cherry_") || path.startsWith("bamboo_")
                || path.startsWith("crimson_") || path.startsWith("warped_")
                || path.startsWith("pale_oak_"));
    }

    private void fireUseKey(Minecraft client) {
        if (RightClickerModule.instance().isSupplyingFire()) return;
        if (client.options == null || client.options.keyUse == null) return;
        InputConstants.Key key = client.options.keyUse.getDefaultKey();
        if (key != null) KeyMapping.click(key);
    }

    private void collectDebugLine(LevelRenderContext context) {
        if (!enabled || !config().debugLine || debugAim == null
                || System.currentTimeMillis() - debugAimAt > 150L) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        Vec3 eye = client.player.getEyePosition(1.0f);
        Vec3 aim = debugAim;
        SubmitNodeCollectorAdapter.submit(context, eye.subtract(camera), aim.subtract(camera));
    }

    private static float[] calculateYawPitch(Vec3 start, Vec3 target) {
        double dx = target.x - start.x;
        double dy = target.y - start.y;
        double dz = target.z - start.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        if (pitch > 90.0f) pitch = 90.0f;
        if (pitch < -90.0f) pitch = -90.0f;
        return new float[]{yaw, pitch};
    }

    private static Vec3 direction(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = Math.sqrt(delta.lengthSqr());
        return length < 1.0E-6 ? new Vec3(0, 0, 1) : delta.scale(1.0 / length);
    }

    private static double dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static double angleTo(Vec3 eye, Vec3 look, double x, double y, double z) {
        Vec3 delta = new Vec3(x - eye.x, y - eye.y, z - eye.z);
        double length = Math.sqrt(delta.lengthSqr());
        if (length < 1.0E-6) return 0.0;
        double dot = (delta.x * look.x + delta.y * look.y + delta.z * look.z) / length;
        return Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
    }

    private static boolean isAliveTarget(LivingEntity entity) {
        return entity != null && !entity.isRemoved() && entity.isAlive()
                && !entity.isDeadOrDying() && entity.getHealth() > 0.0f;
    }

    private static boolean isTarget(LivingEntity entity) {
        return entity != null && AimLeadModule.isTarget(entity);
    }

    private static boolean isChildWolf(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.animal.wolf.Wolf wolf && wolf.isBaby();
    }

    private static boolean isGiant(LivingEntity entity) {
        return entity instanceof Giant;
    }

    /** 恶魂是飞行怪，天生在玩家头顶高空，高度差忽略规则必须放行。 */
    private static boolean isGhast(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.monster.Ghast;
    }

    private static boolean isBadHeadshot(Minecraft client, LivingEntity entity) {
        return client != null && client.player != null
                && ZombiesExplorerModule.instance().isBadHeadshot(entity, client.player.getY());
    }

    private static boolean isSlime(LivingEntity entity) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null) return false;
        return key.getPath().equals("slime") || key.getPath().equals("magma_cube");
    }

    private static boolean isToo(LivingEntity entity) {
        return entity instanceof Zombie zombie
                && ZombiesAssistModule.isTooSignature(zombie,
                ZombiesAssistModule.instance().config().tooStrictGreen);
    }

    private static boolean isClown(Zombie zombie) {
        return ZombiesAssistModule.isClownSignature(zombie);
    }

    private static boolean isVerticalFalling(LivingEntity entity) {
        Vec3 delta = entity.getDeltaMovement();
        return delta.y < -1.2 && delta.x * delta.x + delta.z * delta.z < 0.25;
    }

    /**
     * mid（AA 花坛 / 飞碟四口正下方）内高速自由落体的怪：UFO 从 y≈105 投放、尚未落地，
     * 锁定它们只会浪费子弹并把视角拉向天空。
     * 只在 Alien Arcadium 生效；被打飞（水平速度大）与窗户下落（贴地、不在 mid cell）
     * 都不满足判定，不会被误忽略。
     */
    private boolean isMidFallDrop(LivingEntity entity) {
        if (!ZombiesTracker.instance().isInAlienArcadium()) return false;
        if (!AimbotRules.isInsideMidCell(entity.getX(), entity.getZ())) return false;
        Vec3 motion = entity.getDeltaMovement();
        double horizontal = Math.hypot(motion.x, motion.z);
        return AimbotRules.isHighSpeedFall(entity.getY(), motion.y, horizontal,
                MID_GROUND_Y, config.midFallSpeed, MID_FALL_MAX_HORIZONTAL);
    }

    /**
     * Humanize 扫射（锥内扫动 + 大幅扫描线）的回合门控：回合数小于
     * {@code sweepMinRound} 时完全关闭，只锁单只。
     * 非 Zombies 局或回合未知（round<=0）时保持原有行为，不做门控。
     */
    private boolean sweepSuppressedByRound() {
        if (config.sweepMinRound <= 1) return false;
        if (!ZombiesTracker.instance().isInZombies()) return false;
        int round = ZombiesTracker.instance().round();
        return round > 0 && round < config.sweepMinRound;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class CandidateMeta {
        final LivingEntity entity;
        final boolean threat;
        final boolean too;
        final boolean giant;
        final int group;
        final double angle;
        final double distance;

        CandidateMeta(LivingEntity entity, boolean threat, boolean too, boolean giant,
                      int group, double angle, double distance) {
            this.entity = entity;
            this.threat = threat;
            this.too = too;
            this.giant = giant;
            this.group = group;
            this.angle = angle;
            this.distance = distance;
        }
    }

    private static final class Scored {
        final LivingEntity entity;
        final AimbotRules.Candidate candidate;
        final Vec3 point;
        final int group;
        final double distance;
        final double angle;

        Scored(LivingEntity entity, AimbotRules.Candidate candidate, Vec3 point,
               int group, double distance, double angle) {
            this.entity = entity;
            this.candidate = candidate;
            this.point = point;
            this.group = group;
            this.distance = distance;
            this.angle = angle;
        }
    }

    /** Small isolated adapter around the 26.2 submit pipeline for the debug line. */
    private static final class SubmitNodeCollectorAdapter {
        private SubmitNodeCollectorAdapter() {
        }

        static void submit(LevelRenderContext context, Vec3 from, Vec3 to) {
            net.minecraft.client.renderer.SubmitNodeCollector collector = context.submitNodeCollector();
            PoseStack pose = context.poseStack();
            collector.submitCustomGeometry(pose, EspRenderTypes.espLines(), (stackPose, consumer) -> {
                line(consumer, stackPose, (float) from.x, (float) from.y, (float) from.z,
                        (float) to.x, (float) to.y, (float) to.z);
            });
        }

        private static void line(VertexConsumer consumer, PoseStack.Pose pose,
                                 float x1, float y1, float z1, float x2, float y2, float z2) {
            consumer.addVertex(pose, x1, y1, z1).setColor(0.0f, 1.0f, 0.3f, 0.9f)
                    .setNormal(pose, 0, 1, 0).setLineWidth(2.0f);
            consumer.addVertex(pose, x2, y2, z2).setColor(0.0f, 1.0f, 0.3f, 0.9f)
                    .setNormal(pose, 0, 1, 0).setLineWidth(2.0f);
        }
    }
}

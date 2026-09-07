package dev.micx.micxfabric;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Minecraft-free parts of the 1.8.9 Aimbot target-selection state machine. */
public final class AimbotRules {
    private AimbotRules() {
    }

    /** ZombieCat/Bridger-compatible head layer: the upper 20% of the hitbox. */
    public static double headLayerBottom(double height) {
        return height * 0.80;
    }

    public static boolean isHeadLayer(double y, double footY, double height) {
        return y >= footY + headLayerBottom(height);
    }

    public static double angleDelta(double from, double to) {
        double delta = (to - from) % 360.0;
        if (delta > 180.0) delta -= 360.0;
        if (delta < -180.0) delta += 360.0;
        return delta;
    }

    /**
     * Preferred pitch for an ordinary mob. Minecraft uses negative pitch for
     * looking up, so targets already above the horizon receive an upward
     * reserve. A target below the horizon is allowed to use its real pitch as
     * the fallback because there is no above-horizon line that reaches it.
     */
    public static double normalPitchTarget(double targetPitch, double horizonMarginDeg) {
        if (!Double.isFinite(targetPitch)) return 0.0;
        double margin = Math.max(0.0, Math.abs(horizonMarginDeg));
        double preferred = targetPitch <= 0.0
                ? Math.min(targetPitch - margin, -margin)
                : targetPitch;
        return Math.max(-90.0, Math.min(90.0, preferred));
    }

    /**
     * Applies the above-horizon reserve only when its ray still reaches the
     * target. A distant same-height mob has a very small vertical hitbox in
     * angular terms, so blindly subtracting the reserve would point above it.
     */
    public static double normalPitchTargetWithFallback(double targetPitch,
                                                        double horizonMarginDeg,
                                                        boolean reserveReachable) {
        double preferred = normalPitchTarget(targetPitch, horizonMarginDeg);
        if (!reserveReachable && Double.isFinite(targetPitch) && preferred < targetPitch) {
            return Math.max(-90.0, Math.min(90.0, targetPitch));
        }
        return preferred;
    }

    /** Keeps a manual normal-mob pitch untouched while it remains in range. */
    public static boolean normalPitchCompatible(double currentPitch, double preferredPitch,
                                                 double toleranceDeg) {
        if (!Double.isFinite(currentPitch) || !Double.isFinite(preferredPitch)) return false;
        return Math.abs(currentPitch - preferredPitch)
                <= Math.max(0.0, Math.abs(toleranceDeg));
    }

    /** Returns only an upward correction; positive Minecraft pitch is downward. */
    public static double upwardOnlyPitchDelta(double currentPitch, double targetPitch) {
        if (!Double.isFinite(currentPitch) || !Double.isFinite(targetPitch)) return 0.0;
        return Math.min(0.0, targetPitch - currentPitch);
    }

    /** Bounded step for the Giant exception: never pulls the view downward. */
    public static double upwardOnlyPitchStep(double currentPitch, double targetPitch,
                                             double maxStep) {
        double delta = upwardOnlyPitchDelta(currentPitch, targetPitch);
        double step = Math.max(0.0, Math.abs(maxStep));
        return currentPitch + Math.max(delta, -step);
    }

    /** Bounded non-wrapping step used by the legacy non-humanized fallback. */
    public static double stepToward(double current, double target, double maxStep) {
        double step = Math.max(0.0, Math.abs(maxStep));
        double delta = angleDelta(current, target);
        if (Math.abs(delta) <= step) return current + delta;
        return current + Math.copySign(step, delta);
    }

    /** Only the explicitly supported single slab variants may be the first solid hit. */
    public static boolean isAllowedSingleSlab(String registryPath, boolean doubleSlab) {
        if (doubleSlab || registryPath == null || registryPath.startsWith("double_")) return false;
        return "stone_brick_slab".equals(registryPath) || "oak_slab".equals(registryPath);
    }

    /** Minecraft's yaw/pitch convention, used by the virtual joystick selector. */
    public static Vec3 lookFromAngles(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad),
                Math.cos(yawRad) * cosPitch);
    }

    public static boolean isJoystickSwitching(double yawOffset, double pitchOffset, double switchDeg) {
        return Math.hypot(yawOffset, pitchOffset) >= switchDeg;
    }

    public static boolean joystickHoldCurrent(boolean joystick, boolean switching, boolean currentValid) {
        return joystick && !switching && currentValid;
    }

    public static float[] applyJoystickOffset(float targetYaw, float targetPitch,
                                               double yawOffset, double pitchOffset) {
        double yaw = (targetYaw + yawOffset) % 360.0;
        if (yaw > 180.0) yaw -= 360.0;
        if (yaw < -180.0) yaw += 360.0;
        double pitch = Math.max(-90.0, Math.min(90.0, targetPitch - pitchOffset));
        return new float[]{(float) yaw, (float) pitch};
    }

    /** Returns the closest candidate to a pushed joystick direction, or -1. */
    public static int joystickPickIdx(double[] angleDeg, double coneDeg) {
        int best = -1;
        double bestAngle = coneDeg;
        for (int i = 0; i < angleDeg.length; i++) {
            double angle = Math.abs(angleDeg[i]);
            if (best == -1 ? angle <= coneDeg : angle < bestAngle) {
                best = i;
                bestAngle = angle;
            }
        }
        return best;
    }

    public static double joyDecay(double value, double multiplier) {
        double next = value * multiplier;
        return Math.abs(next) < 0.02 ? 0.0 : next;
    }

    /** Flick state transition. {@code belowAcc[0]} stores low-speed milliseconds. */
    public static boolean flickStep(boolean active, double speedPxPerSecond,
                                    double thresholdPxPerSecond, int exitMs,
                                    long dtMs, int[] belowAcc) {
        if (!active) return speedPxPerSecond > thresholdPxPerSecond;
        if (speedPxPerSecond > thresholdPxPerSecond) {
            belowAcc[0] = 0;
            return true;
        }
        belowAcc[0] += (int) Math.max(0L, dtMs);
        return belowAcc[0] < exitMs;
    }

    /* ---- Humanize target sweep and rotation model, ported from the final 1.8.9 rules ---- */

    public static final double SWEEP_CONE_DEG = 10.0;
    public static final double SWEEP_FRONT_WINDOW_DEG = 30.0;
    public static final int SWEEP_MIN_CONE_COUNT = 2;
    public static final double SWEEP_MIN_SPAN_DEG = 3.0;
    public static final double SWEEP_SCAN_CYCLE_MS = 700.0;
    public static final double SWEEP_SCAN_PEAK_DEG = 9.0;

    /**
     * 前窗兜底挑选：只按转向角最小，不按距离。
     * 用户定稿 2026-09-08：5° 的目标优先于 25° 的目标，距离不是判据。
     */
    public static int frontWindowPickIdx(double[] angleDeg, double windowDeg) {
        int best = -1;
        double bestAngle = Double.MAX_VALUE;
        for (int i = 0; i < angleDeg.length; i++) {
            double angle = Math.abs(angleDeg[i]);
            if (angle > windowDeg) continue;
            if (best < 0 || angle < bestAngle) {
                best = i;
                bestAngle = angle;
            }
        }
        return best;
    }

    /**
     * Humanize 锥内/前窗的目标比较：只比转向角，威胁不再抢在角度前面。
     * 用户定稿 2026-09-08：需要转动的角度最小者胜出（5° 赢 25°）。
     */
    public static int compareSweepCandidates(double aAngleDeg, double bAngleDeg) {
        return Double.compare(Math.abs(aAngleDeg), Math.abs(bAngleDeg));
    }

    public static boolean shouldSweepScan(int count, double spanDeg, double minSpanDeg) {
        return count >= SWEEP_MIN_CONE_COUNT && spanDeg >= minSpanDeg;
    }

    public static double sweepScanAngleDeg(double minDeg, double maxDeg, double phase) {
        double middle = (minDeg + maxDeg) * 0.5;
        double half = (maxDeg - minDeg) * 0.5;
        return middle + half * Math.sin(phase * 2.0 * Math.PI);
    }

    /** Saccade peak speed: large target turns are faster, capped to avoid a snap. */
    public static final double SACCADE_PEAK_PER_DEG = 1.0;
    public static final double SACCADE_PEAK_CAP_DEG_PER_TICK = 60.0;

    public static double saccadePeakDegPerTick(double swingDeg) {
        return Math.min(SACCADE_PEAK_CAP_DEG_PER_TICK, Math.abs(swingDeg));
    }

    public static final double OVERSHOOT_K = 0.12;
    public static final double OVERSHOOT_FW_BASE_DEG = 3.0;
    public static final double OVERSHOOT_FW_MIN = 0.15;
    public static final double OVERSHOOT_CAP_DEG = 25.0;

    public static double overshootForSwing(double swingDeg, double headRadiusDeg) {
        double fw = Math.max(OVERSHOOT_FW_MIN,
                Math.min(1.0, headRadiusDeg / OVERSHOOT_FW_BASE_DEG));
        return Math.min(OVERSHOOT_CAP_DEG, OVERSHOOT_K * Math.abs(swingDeg) * fw);
    }

    /**
     * One signed humanized rotation step. Acceleration is bounded while braking
     * is immediate, which gives a fast start and a continuous settle without a
     * fixed-speed snap.
     */
    public static double humanizeStep(double distance, double velocity, double peakDeg,
                                      double decelStartDeg, double maxAccel,
                                      double overshootDeg) {
        double peak = Math.max(0.0, Math.abs(peakDeg));
        if (peak == 0.0 || !Double.isFinite(distance)) return 0.0;
        double decel = Math.max(peak, Math.abs(decelStartDeg));
        double gain = Math.min(1.0, peak / decel);
        double currentVelocity = Double.isFinite(velocity) ? velocity : 0.0;
        double desired = gain * distance;
        boolean movingToward = currentVelocity * distance > 0.0;
        boolean fast = Math.abs(currentVelocity) > 15.0;
        boolean braking = movingToward && Math.abs(currentVelocity) > Math.abs(gain * distance);
        if (distance != 0.0 && fast && (Math.abs(distance) < 6.0 || braking)) {
            desired += Math.copySign(Math.abs(overshootDeg), distance);
        }
        desired = Math.max(-peak, Math.min(peak, desired));
        double acceleration = Math.max(0.0, Math.abs(maxAccel));
        double next;
        if (acceleration == 0.0) {
            next = desired;
        } else if (desired > currentVelocity) {
            next = Math.min(currentVelocity + acceleration, desired);
        } else {
            next = Math.max(currentVelocity - acceleration, desired);
        }
        return Math.max(-peak, Math.min(peak, next));
    }

    /** Angular radius of the upper hitbox, used as a non-point lock range. */
    public static double headRadiusDeg(double horizontalDistance) {
        if (horizontalDistance <= 0.1) return 45.0;
        return Math.toDegrees(Math.atan(0.3 / horizontalDistance));
    }

    public static boolean faceUpStep(boolean on, boolean targetAlive, boolean inRange,
                                     int holdTicks, int[] exitCount) {
        int required = Math.max(1, holdTicks);
        if (!on) {
            exitCount[0] = 0;
            return targetAlive && inRange;
        }
        if (!targetAlive || inRange) {
            exitCount[0] = 0;
            return targetAlive;
        }
        exitCount[0]++;
        if (exitCount[0] >= required) {
            exitCount[0] = 0;
            return false;
        }
        return true;
    }

    public static boolean faceUpTrigger(double horizontalDistance, double threshold) {
        return horizontalDistance <= threshold;
    }

    public static double faceUpPitchTarget(double monsterTopY, double eyeY, double horizontalDistance) {
        if (monsterTopY > eyeY) return -90.0;
        return -Math.toDegrees(Math.atan2(monsterTopY - eyeY,
                Math.max(horizontalDistance, 1.0E-6)));
    }

    public static double ar1Step(double error, double alpha, double sigma, Random random) {
        double noise = random == null ? 0.0 : (random.nextDouble() * 2.0 - 1.0) * sigma;
        return alpha * error + noise;
    }

    public static double correctionSettle(double lockRadius, Random random) {
        if (lockRadius <= 0.0 || random == null) return 0.0;
        double roll = random.nextDouble();
        if (roll < 0.60) return 0.0;
        if (roll < 0.90) return lockRadius * (0.15 + 0.45 * random.nextDouble());
        return lockRadius * 0.8;
    }

    public static int reactionDelayTicks(Random random) {
        return random == null ? 4 : 3 + random.nextInt(4);
    }

    public static double tremor(Random random, double amplitudeDeg) {
        if (random == null) return 0.0;
        return (random.nextDouble() * 2.0 - 1.0) * Math.abs(amplitudeDeg);
    }

    public static double pathArcOffset(double progress, double amplitudeDeg) {
        if (progress <= 0.0 || progress >= 1.0) return 0.0;
        return amplitudeDeg * Math.sin(progress * Math.PI);
    }

    public static double yawFollowGain(double horizontalDistance, double fullGainDistance) {
        if (horizontalDistance <= 0.0 || fullGainDistance <= 0.0) return 0.0;
        return Math.min(1.0, horizontalDistance / fullGainDistance);
    }

    public static double nearSweepIntervalMs(Random random) {
        return nearSweepIntervalMs(random, 450.0);
    }

    public static double nearSweepIntervalMs(Random random, double meanMs) {
        double mean = Math.max(75.0, Math.min(2000.0, meanMs));
        if (random == null) return mean;
        double u = Math.max(1.0E-6, random.nextDouble());
        return Math.min(mean * 2.0, -mean * Math.log(u));
    }

    /** 弹道阶段抑制手部噪声：距离目标越远越干净，接近锁定范围才恢复微扰。 */
    public static double tremorSuppressionScale(double distanceDeg, double rampDeg) {
        if (rampDeg <= 0.0) return 1.0;
        return Math.max(0.0, Math.min(1.0, (rampDeg - distanceDeg) / rampDeg));
    }

    /** 近距离随机扫动幅度封顶，避免贴脸时按 hitbox 角半径产生大幅左右摆头。 */
    public static final double NEAR_SWEEP_ERR_CAP_DEG = 3.0;

    public static double nearSweepErrAmplitude(double lockRadius) {
        return Math.min(Math.max(0.0, lockRadius * 0.8), NEAR_SWEEP_ERR_CAP_DEG);
    }

    public static double killPauseMs(Random random) {
        return random == null ? 50.0 : 100.0 * random.nextDouble();
    }

    public static final double SWITCH_TWO_STEP_DEG = 40.0;
    public static final double SWITCH_HEAD_TURN_MARGIN_DEG = 30.0;
    public static final double SWITCH_HEAD_TURN_BUDGET_MS = 300.0;

    public static double headTurnMidAngle(double current, double target, double marginDeg) {
        double delta = angleDelta(current, target);
        if (Math.abs(delta) <= marginDeg) return target;
        return angleDelta(0.0, current + delta - Math.copySign(marginDeg, delta));
    }

    public static double targetAngularVelocityDegPerSec(double previous, double current, double dtMs) {
        if (dtMs <= 0.0 || dtMs > 200.0) return 0.0;
        return angleDelta(previous, current) / (dtMs / 1000.0);
    }

    public static boolean pursuitNeeded(double yawVelocityDegPerSec, double pitchVelocityDegPerSec,
                                        double minimumDegPerSec) {
        return Math.abs(yawVelocityDegPerSec) >= minimumDegPerSec
                || Math.abs(pitchVelocityDegPerSec) >= minimumDegPerSec;
    }

    /**
     * Smooth-pursuit gate with separate enter/exit thresholds. Without the
     * exit threshold, strafing around the trigger speed repeatedly changes the
     * controller branch and produces a visible stop-pull-stop oscillation.
     */
    public static boolean pursuitNeededWithHysteresis(boolean active,
                                                       double yawVelocityDegPerSec,
                                                       double pitchVelocityDegPerSec,
                                                       double enterDegPerSec,
                                                       double exitDegPerSec) {
        double enter = Math.max(0.0, Math.abs(enterDegPerSec));
        double exit = Math.min(enter, Math.max(0.0, Math.abs(exitDegPerSec)));
        double velocity = Math.max(Math.abs(yawVelocityDegPerSec),
                Math.abs(pitchVelocityDegPerSec));
        return velocity >= (active ? exit : enter);
    }

    /**
     * zbc9-style incremental rotation with an eased remaining error. The
     * configured value is the maximum step at a 180-degree error; every
     * smaller error gets the same proportionally smaller delta. Keeping this
     * proportional instead of returning a fixed step avoids a visible
     * stop-and-go staircase when the target is outside the initial view.
     */
    public static double smoothRotationStep(double deltaDeg, double maxStepDeg) {
        if (!Double.isFinite(deltaDeg)) return 0.0;
        double maxStep = Math.max(0.0, Math.abs(maxStepDeg));
        if (maxStep == 0.0) return 0.0;
        if (Math.abs(deltaDeg) <= 0.05) return deltaDeg;
        double gain = Math.min(1.0, maxStep / 180.0);
        return Math.copySign(Math.min(Math.abs(deltaDeg), Math.abs(deltaDeg) * gain), deltaDeg);
    }

    /**
     * Aggressive shortest-path rotation: no proportional easing, randomness or
     * overshoot. The render consumer can still split this controller delta
     * across frames, so a large turn is forceful without restoring a 20 Hz
     * camera staircase.
     */
    public static double bruteRotationStep(double deltaDeg, double maxStepDeg) {
        if (!Double.isFinite(deltaDeg)) return 0.0;
        double maxStep = Math.max(0.0, Math.abs(maxStepDeg));
        if (maxStep == 0.0) return 0.0;
        return Math.copySign(Math.min(Math.abs(deltaDeg), maxStep), deltaDeg);
    }

    /**
     * Splits a controller's one-tick turn across render frames. At 60 FPS a
     * 30-degree decision becomes three 10-degree writes instead of one 30°
     * camera snap, while a full 50 ms interval still consumes exactly 30°.
     */
    public static double renderStepFromTickDelta(double tickDeltaDeg, double elapsedSeconds,
                                                 double controllerTickSeconds) {
        if (!Double.isFinite(tickDeltaDeg) || !Double.isFinite(elapsedSeconds)
                || !Double.isFinite(controllerTickSeconds) || controllerTickSeconds <= 0.0) {
            return 0.0;
        }
        return tickDeltaDeg * Math.max(0.0, elapsedSeconds) / controllerTickSeconds;
    }

    public static double pursuitStep(double targetVelocityDegPerSec, double noiseDeg, Random random) {
        return targetVelocityDegPerSec / 20.0 + tremor(random, noiseDeg);
    }

    public static final double ROT_QUANT_STEP_DEG = 0.05;

    public static double quantizeRotation(double deltaDeg) {
        if (!Double.isFinite(deltaDeg)) return 0.0;
        return Math.round(deltaDeg / ROT_QUANT_STEP_DEG) * ROT_QUANT_STEP_DEG;
    }

    public static int pickClosestIdx(double[] angleDeg) {
        int best = -1;
        double bestAngle = Double.MAX_VALUE;
        for (int i = 0; i < angleDeg.length; i++) {
            double angle = Math.abs(angleDeg[i]);
            if (angle < bestAngle) {
                best = i;
                bestAngle = angle;
            }
        }
        return best;
    }

    public static boolean isThreat(double horizontalDistance, double threatDistance,
                                   long threatUntil, long now) {
        return horizontalDistance <= threatDistance || now < threatUntil;
    }

    /** Sort summary: negative means {@code a} should be selected before {@code b}. */
    public static final class Candidate {
        public final boolean threat;
        public final boolean too;
        public final boolean giant;
        public final boolean headLine;
        public final int penCount;
        public final double angle;

        public Candidate(boolean threat, boolean too, boolean giant,
                         boolean headLine, int penCount, double angle) {
            this.threat = threat;
            this.too = too;
            this.giant = giant;
            this.headLine = headLine;
            this.penCount = penCount;
            this.angle = angle;
        }
    }

    public static int compareCandidates(Candidate a, Candidate b) {
        if (a.threat != b.threat) return a.threat ? -1 : 1;
        if (a.too != b.too) return a.too ? -1 : 1;
        if (a.giant != b.giant) return a.giant ? -1 : 1;
        if (a.headLine != b.headLine) return a.headLine ? -1 : 1;
        if (a.penCount != b.penCount) return Integer.compare(a.penCount, b.penCount);
        return Double.compare(a.angle, b.angle);
    }

    public static boolean shouldSwitch(Candidate current, Candidate best, boolean earlyRound) {
        if (earlyRound) return best.threat && !current.threat;
        if (current.threat != best.threat) return best.threat;
        if (current.too != best.too) return best.too;
        if (current.giant != best.giant) return best.giant;
        if (current.headLine != best.headLine) return best.headLine;
        return best.penCount < current.penCount;
    }

    public static int groupRank(boolean prioBaby, boolean prioClown, boolean prioGiant,
                                boolean baby, boolean clown, boolean giant) {
        if (prioBaby && baby) return 2;
        if (prioClown && clown) return 1;
        if (prioGiant && giant) return 1;
        return 0;
    }

    public static boolean closestBetter(double newDistance, double currentDistance, double margin) {
        return currentDistance - newDistance > margin;
    }

    /* ---- mid（UFO 飞碟投放区）高空坠怪过滤 ---- */

    /**
     * mid 花坛 cell（AA 地图中央走廊）：UFO 四口 (±2, 105, 12/14) 正下方。
     * 实测 2963 只空中怪 94% 集中在这四口，落地散布也在此 cell 内。
     * 窗户位于地图四周外墙，落点不可能落进这个范围。
     */
    public static final double MID_CELL_MIN_X = -3.0;
    public static final double MID_CELL_MAX_X = 3.0;
    public static final double MID_CELL_MIN_Z = 11.0;
    public static final double MID_CELL_MAX_Z = 15.0;

    public static boolean isInsideMidCell(double x, double z) {
        return x >= MID_CELL_MIN_X && x <= MID_CELL_MAX_X
                && z >= MID_CELL_MIN_Z && z <= MID_CELL_MAX_Z;
    }

    /**
     * 高速自由落体判定。三个条件同时成立才算：
     * <ol>
     *   <li>脚底仍明显高于地面（{@code y > groundY}）——窗户下落是贴地短距，直接排除；</li>
     *   <li>垂直速度超过阈值——飞碟从 y≈105 投放的自由落体远快于任何短距下落；</li>
     *   <li>水平速度接近零——被击退/打飞的怪有明显的水平位移，不能被忽略。</li>
     * </ol>
     */
    /**
     * 目标是否高悬在玩家头顶：脚底高度差超过阈值。
     * 恶魂（Ghast）是飞行怪，调用方必须先豁免，否则会被高度规则全部吃掉。
     */
    public static boolean isTooHighAbove(double footY, double playerFootY, double threshold) {
        if (!Double.isFinite(footY) || !Double.isFinite(playerFootY)) return false;
        return footY - playerFootY > Math.max(0.0, threshold);
    }

    public static boolean isHighSpeedFall(double y, double motionY, double horizontalSpeed,
                                          double groundY, double fallSpeed, double maxHorizontal) {
        if (!Double.isFinite(y) || !Double.isFinite(motionY)) return false;
        if (y <= groundY) return false;
        if (motionY >= -Math.abs(fallSpeed)) return false;
        return Math.abs(horizontalSpeed) <= Math.abs(maxHorizontal);
    }
}

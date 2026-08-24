package dev.micx.micxfabric;

/**
 * 吸附模块纯函数规则（2.25.16）。
 *
 * <p>吸附 = 准心靠近怪物时轻微吸向目标瞄准点（眼睛高度/爆头点），完全不屏蔽鼠标输入：
 * 鼠标位移由原版渲染帧照常叠加，本模块只额外写入一个微小视角修正。所有可测逻辑集中在此。
 */
public final class MagnetRules {

    private MagnetRules() { }

    /** 帧率归一强度：以 0.1°/帧@60fps 为基准，按实际帧时长线性缩放（120fps 下减半）。 */
    public static double frameNormalizedStep(double strengthPerFrame60, double dtSec) {
        return strengthPerFrame60 * Math.max(dtSec, 0.0) * 60.0;
    }

    /** 单轴修正量（°）：向目标方向移动 min(|分量差|, 步长)，符号随目标方向；步长 ≤ 0 时不修正。 */
    public static double axisCorrection(double diffDeg, double stepDeg) {
        if (stepDeg <= 0.0) return 0.0;
        double abs = Math.abs(diffDeg);
        double move = Math.min(abs, stepDeg);
        return diffDeg >= 0.0 ? move : -move;
    }

    /** 总角距（°）：yaw/pitch 分量差的欧几里得近似。 */
    public static double totalAngle(double yawDiffDeg, double pitchDiffDeg) {
        return Math.sqrt(yawDiffDeg * yawDiffDeg + pitchDiffDeg * pitchDiffDeg);
    }

    /** 是否停止修正：总角距已进入爆头范围（吸到头部附近即停，不完全锁死）。
     *  stopDeg 由调用方动态化（headshotStopRange：贴脸松、远处紧，aimbot 同款"锁范围不锁点"）。 */
    public static boolean withinHeadshotRange(double yawDiffDeg, double pitchDiffDeg, double stopDeg) {
        return totalAngle(yawDiffDeg, pitchDiffDeg) <= stopDeg;
    }

    /** 爆头角半径（°）：怪宽一半（0.3 格）的视角角跨度（与 AimbotRules.headRadiusDeg 同公式）。
     *  2.26.6 吸附停拉范围动态化：贴脸松（0.5 格 ≈ 31°）、远距离紧（30 格 ≈ 0.57°）——
     *  返回 max(爆头角半径, 兜底 stopDeg)：远距离不因角半径过小而无休止修正。
     *  自含公式不依赖 aimbot 包（common 分支无 aimbot 类，模块需独立可 cherry-pick）。 */
    public static double headshotStopRange(double horizontalDist, double stopDeg) {
        if (horizontalDist <= 0.1) return 45.0;
        double radius = Math.toDegrees(Math.atan(0.3 / horizontalDist));
        return Math.max(radius, stopDeg);
    }

    /** 拟人化牵引（2.26.6，aimbot humanize 同源）：每帧按剩余差距的固定比例收敛（先快后慢）——
     *  差距大拉得快（8° 边缘 ~14°/s）、接近爆头范围拉得轻（~3°/s），替代恒定速度直线拉
     *  （真人压枪收尾感）。ratePerSec = 每秒收敛率（e 指数），dtSec 为帧时长；永不超过差距、保号。 */
    public static double humanStep(double diffDeg, double ratePerSec, double dtSec) {
        return diffDeg * (1.0 - Math.exp(-ratePerSec * Math.max(dtSec, 0.0)));
    }

    /** 幽灵框相对真身的位置偏移余量（格，2.26.9）：AimLead τ 外推 0.35s × ~5.7 m/s ≈ 2 格。
     *  pickTarget 粗筛用：真身角距 ≤ 半径 + 该余量换算角度时，幽灵框才可能进入吸附半径。 */
    public static final double GHOST_MAX_OFFSET_BLOCKS = 2.0;

    /** 幽灵框偏移造成的角度余量（弧度，2.26.9 性能）：水平距离越近偏移角越大。
     *  真身角距超出「半径 + 此余量」的目标，幽灵框不可能进半径——无需调 AimLead
     *  精算（其内部做世界碰撞查询，怪堆时每帧 N 只 × 数次 = 怪物多掉帧根因）。
     *  距离下限 2.5 格：贴脸怪余量封顶，不误挡。 */
    public static double ghostSlackRad(double horizDist) {
        return Math.atan(GHOST_MAX_OFFSET_BLOCKS / Math.max(horizDist, 2.5));
    }

    /** 吸附半径筛选（°）：准心与目标瞄准点夹角超过半径的目标不吸。 */
    public static boolean withinRadius(double totalAngleDeg, double radiusDeg) {
        return totalAngleDeg <= radiusDeg;
    }

    /** 减速带判定：目标总角距在减速半径内才降低鼠标灵敏度。 */
    public static boolean shouldSlow(double totalAngleDeg, double radiusDeg) {
        return totalAngleDeg <= radiusDeg;
    }

    /** 目标选择：返回与准心总角距最小的候选下标；无候选返回 -1。 */
    public static int pickClosestAngleIdx(double[] totalAngles) {
        int best = -1;
        double min = Double.MAX_VALUE;
        for (int i = 0; i < totalAngles.length; i++) {
            if (totalAngles[i] < min) {
                min = totalAngles[i];
                best = i;
            }
        }
        return best;
    }

    /** 灵敏度乘数结果钳制：落回 [0.05, 1.0]（滑杆边界外手改配置也不出界）。 */
    public static float clampedSensitivity(float base, double factor) {
        double v = base * factor;
        if (v > 1.0f) return 1.0f;
        if (v < 0.05f) return 0.05f;
        return (float) v;
    }

    /** 目标类型过滤：Slime/Golem/Giant 是否纳入由开关决定；其他敌对生物恒纳入。
     *  2.26.15 加巨人开关（用户要求：允许配置是否吸附巨人；Zombies 巨人默认吸）。 */
    public static boolean acceptsEntityType(boolean isSlime, boolean isGolem, boolean isGiant,
                                            boolean includeSlime, boolean includeGolem, boolean includeGiant) {
        if (isSlime) return includeSlime;
        if (isGolem) return includeGolem;
        if (isGiant) return includeGiant;
        return true;
    }

    /**
     * 牵引强度按鼠标速度衰减（2.25.16 修复"牵引+鼠标同动一顿一顿"）：鼠标不动/慢移时全强度，
     * 移动越快牵引越弱，超过 high 归零——手瞄时牵引完全退场，静止微调时恢复吸附。
     * 鼠标速度用帧间视角变化推断（不读 Mouse.getDX/DY，完全不吞鼠标）。
     */
    public static double speedFactor(double mouseSpeedDegPerSec,
                                     double lowDegPerSec, double highDegPerSec) {
        if (mouseSpeedDegPerSec <= lowDegPerSec) return 1.0;
        if (mouseSpeedDegPerSec >= highDegPerSec) return 0.0;
        return 1.0 - (mouseSpeedDegPerSec - lowDegPerSec) / (highDegPerSec - lowDegPerSec);
    }

    /** 减速带甩枪脱困系数（2.26.15 用户实测：切目标甩不动）：鼠标慢移（≤low）→ 减速
     *  全量（slowFactor 原样）；快速甩动（≥high）→ 系数 1.0（灵敏度完全恢复，紧急目标
     *  不被黏住），中间线性过渡。与 speedFactor（牵引衰减）同构、方向相反。 */
    public static double slowEscapeFactor(double slowFactor, double mouseSpeedDegPerSec,
                                          double lowDegPerSec, double highDegPerSec) {
        if (mouseSpeedDegPerSec <= lowDegPerSec) return slowFactor;
        if (mouseSpeedDegPerSec >= highDegPerSec) return 1.0;
        double t = (mouseSpeedDegPerSec - lowDegPerSec) / (highDegPerSec - lowDegPerSec);
        return slowFactor + t * (1.0 - slowFactor);
    }

    /**
     * 射线-AABB 相交（slab 算法）：准心射线（起点 + 方向单位向量）与目标 hitbox 相交判定。
     * 返回沿射线的最近交点距离 t（起点在 box 内时 t=0，同样视为命中）；不相交/反向返回 -1。
     */
    public static double rayIntersectsAabb(double ox, double oy, double oz,
                                           double dx, double dy, double dz,
                                           double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ) {
        double[] entryExit = slabEntryExit(ox, oy, oz, dx, dy, dz,
                minX, minY, minZ, maxX, maxY, maxZ);
        return entryExit == null ? -1.0 : entryExit[0];
    }

    /**
     * 射线穿过 AABB 的弦长（2.25.16 粘性渐变）：准心穿过怪物体积的深度 = 离开交点 - 进入交点。
     * 对准中心穿最深（最长弦）→ 满粘性；擦边穿过很浅 → 低粘性。未命中返回 -1。
     */
    public static double rayChordDepth(double ox, double oy, double oz,
                                       double dx, double dy, double dz,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        double[] entryExit = slabEntryExit(ox, oy, oz, dx, dy, dz,
                minX, minY, minZ, maxX, maxY, maxZ);
        if (entryExit == null) return -1.0;
        return entryExit[1] - entryExit[0];
    }

    /**
     * 粘性系数（0~1）：弦深 0（擦边）→ 0（不减速）；≥ fullDepth 格（对准中心）→ 1（满减速）。
     * 线形渐变，消除"一进 hitbox 瞬间大幅度变黏"的跳变。
     */
    public static double stickyFactor(double chordDepth, double fullDepth) {
        if (chordDepth <= 0.0) return 0.0;
        if (chordDepth >= fullDepth) return 1.0;
        return chordDepth / fullDepth;
    }

    /** 粘性灵敏度：满粘性 → 原灵敏度 × slowFactor；擦边 → 原灵敏度不变。 */
    public static float slowedSensitivity(float base, double slowFactor, double sticky) {
        double s = Math.max(0.0, Math.min(1.0, sticky));
        return clampedSensitivity(base, 1.0 - s * (1.0 - slowFactor));
    }

    /** slab 算法核心：返回 [进入 t, 离开 t]；不相交/反向返回 null。 */
    private static double[] slabEntryExit(double ox, double oy, double oz,
                                          double dx, double dy, double dz,
                                          double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ) {
        double tmin = 0.0;
        double tmax = Double.MAX_VALUE;
        // 三个 slab 展开（x/y/z），避免数组分配
        if (Math.abs(dx) < 1.0E-8) {
            if (ox < minX || ox > maxX) return null;
        } else {
            double t1 = (minX - ox) / dx;
            double t2 = (maxX - ox) / dx;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return null;
        }
        if (Math.abs(dy) < 1.0E-8) {
            if (oy < minY || oy > maxY) return null;
        } else {
            double t1 = (minY - oy) / dy;
            double t2 = (maxY - oy) / dy;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return null;
        }
        if (Math.abs(dz) < 1.0E-8) {
            if (oz < minZ || oz > maxZ) return null;
        } else {
            double t1 = (minZ - oz) / dz;
            double t2 = (maxZ - oz) / dz;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return null;
        }
        return tmin >= 0.0 ? new double[]{tmin, tmax} : null;
    }

    /** 角度归一化到 [-180, 180)。 */
    public static double normalizeDeg(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    /**
     * yaw 差（°）：目标水平方向相对玩家当前 yaw 的差，正值 = 目标在左侧（需左转）。
     * MC 约定：yaw 逆时针为正（看 +Z = 0，看 +X = -90），rotationYaw += 负值 = 右转。
     */
    public static double yawToTarget(double targetDirX, double targetDirZ, double playerYawDeg) {
        double targetYaw = Math.toDegrees(Math.atan2(-targetDirX, targetDirZ));
        return normalizeDeg(targetYaw - playerYawDeg);
    }

    /**
     * pitch 差（°）：目标方向相对玩家当前 pitch 的差，正值 = 目标在下方（需低头）。
     * MC 约定：rotationPitch = -asin(look.y)（看天 = -90），rotationPitch += 负值 = 抬头。
     */
    public static double pitchToTarget(double targetDirY, double playerPitchDeg) {
        double clamped = Math.max(-1.0, Math.min(1.0, targetDirY));
        return -Math.toDegrees(Math.asin(clamped)) - playerPitchDeg;
    }
}

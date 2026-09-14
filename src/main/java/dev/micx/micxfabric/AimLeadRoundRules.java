package dev.micx.micxfabric;

/**
 * AimLead 的纯规则参数，独立于 Minecraft，便于回归验证。
 *
 * <p>v0.2.11：自 Forge 1.8.9 aimlead/AimLeadRoundRules 逐常量逐函数移植
 * （对照 git 基线 2026-08-25），行为必须与 Forge 完全一致——任何数值差异
 * 都会直接改变幽灵框体感。单测见 AimLeadRoundRulesTest（Forge 用例全量移植）。</p>
 */
public final class AimLeadRoundRules {

    /** 完成 R25 后，从 R26 起停止 AimLead；新局 R1 自动恢复。 */
    public static final int AUTO_DISABLE_AFTER_ROUND = 25;

    /** 位置停止更新多久后，将预测框收回实体本体（怪停 → 广播停 → 快速收回）。 */
    public static final long SERVER_STALE_MS = 250L;
    /** 用最近窗口的净位移（3D，含垂直）识别急停。 */
    public static final long STOP_WINDOW_MS = 160L;
    public static final double STOP_DISPLACEMENT_BLOCKS = 0.10;
    /** 帧间平滑：提高跟手性；回收到本体时进一步加快。 */
    public static final double MOVING_SMOOTH = 0.78;
    public static final double RETURN_SMOOTH = 0.92;

    /** 外推位移低于此值不画（直接瞄即可）；滞回消失阈值（showing 时更早收回）。 */
    public static final double SHOW_LEAD_BLOCKS = 0.3;
    public static final double HIDE_LEAD_BLOCKS = 0.15;

    /** 垂直运动显著阈值 (m/s)：超过即视为垂直移动（下落/上抛），垂直方向强制外推不收缩。 */
    public static final double VERTICAL_MOVE_THRESHOLD = 1.0;
    /** 垂直速度短窗样本对数：重力方向翻转（上抛顶点）后最新 N 对立即跟随。 */
    public static final int VERTICAL_SHORT_WINDOW_PAIRS = 3;

    /** 水平速度尖峰阈值 (m/s)：超过视为水平击退/传送尖峰，剔除该速度对。 */
    public static final double HORIZONTAL_SPIKE_M_S = 8.0;
    /** 上抛速度尖峰阈值 (m/s)：正 vy 超过视为上抛击退/向上瞬移，剔除。
     *  下落（vy<0）永不剔除——自由落体投放/击退回落是真实运动，速度可达 20~60 m/s。 */
    public static final double UPWARD_SPIKE_M_S = 8.0;
    /** 下落速度保底 (m/s)：|vy| 超过 MC 终端速度(~78)即视为向下瞬移毛刺，剔除。 */
    public static final double DOWNWARD_TELEPORT_M_S = 80.0;
    /** 急停还需中位数速度低于此值：慢速持续运动（缓慢下落/平移）不算停。 */
    public static final double STOP_MEDIAN_SPEED_M_S = 0.25;
    /** 停更但仍在动的速度下限 (m/s)：停更 + 中位数速度 ≥ 此值 = 慢速怪广播间隔大，仍在动，不收缩。 */
    public static final double SLOW_BROADCAST_SPEED_M_S = 0.3;
    /** 垂直外推量显示阈值（比水平低：垂直方向命中误差敏感——爆头高度）。 */
    public static final double VERTICAL_SHOW_LEAD_BLOCKS = 0.10;
    public static final double VERTICAL_HIDE_LEAD_BLOCKS = 0.04;

    /* ---- 转向检测 + τ 缩放（打转怪治乱：转向中缩短提前量，框仍正常显示） ---- */

    /** 角速度下限 (deg/s)：低于此值视为直行，提前量不缩放。 */
    public static final double TURN_DEG_PER_SEC = 90.0;
    /** 角速度上限 (deg/s)：超过视为打转/寻路失败（原地绕圈）。 */
    public static final double CIRCLE_DEG_PER_SEC = 180.0;
    /** 转向中的提前量缩放（方向未定，折中）。 */
    public static final double TAU_SCALE_TURNING = 0.35;
    /** 打转（原地绕圈）的提前量缩放：框贴本体，不再甩在切线上。 */
    public static final double TAU_SCALE_CIRCLING = 0.15;
    /** 转向检测取中位数的角速度样本对数。 */
    public static final int TURN_WINDOW_PAIRS = 3;
    /** 脚下抬升上限（格）：一个方块内的高差（台阶 0.6 / 半砖 / 上坡）都跟得住。 */
    public static final double MAX_FOOT_RISE = 1.2;

    private AimLeadRoundRules() { }

    /** 360° 内两角之差（度）。 */
    public static double angleDeltaDeg(double fromDeg, double toDeg) {
        double d = (toDeg - fromDeg) % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    /**
     * 相邻速度对的转向角速度 (deg/s)：|atan2 夹角| / 时间间隔。
     * 任一端速度为 0（停在原地/样本重复）返回 -1 = 无效，由调用方忽略。
     */
    public static double turnDegPerSec(double vxa, double vza, double vxb, double vzb, double dtSec) {
        double sa = Math.sqrt(vxa * vxa + vza * vza);
        double sb = Math.sqrt(vxb * vxb + vzb * vzb);
        if (dtSec <= 1.0e-4 || sa < 1.0e-3 || sb < 1.0e-3) return -1.0;
        double cross = vxa * vzb - vza * vxb;
        double dot = vxa * vxb + vza * vzb;
        double deg = Math.toDegrees(Math.atan2(Math.abs(cross), dot));
        return deg / dtSec;
    }

    /** 转向 → 提前量缩放：打转 ×0.15，转向中 ×0.35，直行/无效 ×1.0。 */
    public static double tauScale(double turnDegPerSec) {
        if (!(turnDegPerSec > 0.0)) return 1.0;
        if (turnDegPerSec >= CIRCLE_DEG_PER_SEC) return TAU_SCALE_CIRCLING;
        if (turnDegPerSec >= TURN_DEG_PER_SEC) return TAU_SCALE_TURNING;
        return 1.0;
    }

    /** 打转判定（诊断显示，阈值与 {@link #tauScale} 一致）。 */
    public static boolean isCircling(double turnDegPerSec) {
        return turnDegPerSec >= CIRCLE_DEG_PER_SEC;
    }

    /**
     * 是否需要在预测落点处查地面面高（地形跟随）。
     *
     * <p>与旧 groundClampNeeded 的差别：旧逻辑「预测点低于脚底才查」只防穿地；
     * 地形跟随要处理上坡/上台阶/上半砖——落点面高会【高于】脚底，旧逻辑不触发
     * → 框陷在台阶里。这里只要预测点够得着（面高可能落在 [py, footY+抬升上限]）就查，
     * 真正的夹取在 surfaceAt。
     */
    public static boolean needsTerrainClamp(double predictedY, double footY) {
        return predictedY < footY + MAX_FOOT_RISE;
    }

    /**
     * 落点面高是否够得着（地形跟随的上限判断，surfaceAt / 夹取共用）。
     * 高于脚下抬升上限的面（墙、两格台阶）= 不可站立，不能拿来当预测高度。
     */
    public static boolean withinFootRise(double surfaceY, double footY) {
        return surfaceY <= footY + MAX_FOOT_RISE;
    }

    /** 未解析到回合时保留现有行为；R1..R25 开启，R26+ 关闭。 */
    public static boolean activeForRound(int round) {
        return round <= 0 || round <= AUTO_DISABLE_AFTER_ROUND;
    }

    /** 允许同一世界内从上一局末段回到新局 R1。 */
    public static boolean isNewGameRound(int previousRound, int incomingRound) {
        return incomingRound == 1 && previousRound >= AUTO_DISABLE_AFTER_ROUND;
    }

    public static boolean isStopped(long windowAgeMs, double displacement3d, double medianSpeed3d) {
        return windowAgeMs >= STOP_WINDOW_MS
                && displacement3d < STOP_DISPLACEMENT_BLOCKS
                && medianSpeed3d < STOP_MEDIAN_SPEED_M_S;
    }

    /**
     * 预测点是否需要查地面下限（旧口径，保留供回归参考）：返回值 ≤ floor(footY)+1，
     * 预测点 ≥ floor(footY)+1 时钳制数学上必无效。地形跟随已改用
     * {@link #needsTerrainClamp}——旧口径拿不到上坡/台阶的面高抬升。
     */
    public static boolean groundClampNeeded(double predictedY, double footY) {
        return predictedY < Math.floor(footY) + 1.0;
    }

    /**
     * 停更判定：停更时长超阈值 **且** 中位数速度也低于慢速下限才算真停 → 收缩。
     * 停更但速度持续 = 慢速怪广播间隔大（位移广播阈值 0.0625 格），仍在动，不收缩。
     */
    public static boolean isServerStale(long staleMs, double medianSpeed3d) {
        return staleMs > SERVER_STALE_MS && medianSpeed3d < SLOW_BROADCAST_SPEED_M_S;
    }

    /**
     * 该速度对是否为尖峰应剔除。水平 > 8（击退/传送）、上抛 > 8（击退/瞬移）、
     * 超终端速度的下落（向下瞬移）→ 剔除；正常下落（vy<0）永不剔除。
     */
    public static boolean rejectVelocityPair(double vx, double vy, double vz) {
        double horiz = Math.sqrt(vx * vx + vz * vz);
        if (horiz > HORIZONTAL_SPIKE_M_S) return true;
        if (vy > UPWARD_SPIKE_M_S) return true;
        return vy < -DOWNWARD_TELEPORT_M_S;
    }

    /**
     * 垂直速度：取最新 VERTICAL_SHORT_WINDOW_PAIRS 对样本的中位数。
     *
     * <p>全窗中位数会被"上抛(+) + 下落(−)"混合抹平成 ≈0 → 垂直 lead ≈0 → 收缩到本体，
     * 垂直零预判。短窗让重力方向翻转（上抛顶点）后立即跟随；中位数抗单对击退尖峰。
     * pvy 前 m 个必须是最新 m 对（调用方保证收集顺序）。pairs == 0 返回 0。</p>
     */
    public static double verticalVelocity(double[] pvy, int pairs) {
        int m = Math.min(VERTICAL_SHORT_WINDOW_PAIRS, pairs);
        if (m == 0) return 0.0;
        double[] buf = new double[m];
        System.arraycopy(pvy, 0, buf, 0, m);
        java.util.Arrays.sort(buf);
        return (m & 1) == 1 ? buf[m / 2] : (buf[m / 2 - 1] + buf[m / 2]) * 0.5;
    }

    /**
     * holdBack 判定（收缩到本体 / 保持外推）。
     *
     * <p>停更或急停 → 收缩；垂直运动显著（|vy| ≥ 1 m/s）→ 即使水平 lead 不足也强制外推
     * （"完全无水平移动的垂直掉落怪"）；缓慢下坠/平移怪水平 lead 常不足 0.3 格，
     * 但垂直外推量 ≥ 0.1 格就显示（爆头高度敏感）；否则按水平 lead 滞回。</p>
     */
    public static boolean shouldHoldBack(boolean serverStale, boolean justStopped,
                                         double verticalSpeed, double horizontalLead,
                                         double verticalLead, boolean showing) {
        if (serverStale || justStopped) return true;
        if (Math.abs(verticalSpeed) >= VERTICAL_MOVE_THRESHOLD) return false;
        if (verticalLead >= (showing ? VERTICAL_HIDE_LEAD_BLOCKS : VERTICAL_SHOW_LEAD_BLOCKS)) return false;
        return horizontalLead < (showing ? HIDE_LEAD_BLOCKS : SHOW_LEAD_BLOCKS);
    }
}

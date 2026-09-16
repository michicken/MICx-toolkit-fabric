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

    /**
     * BadHeadShot 怪的默认瞄准高度系数（作用在幽灵框上）。
     *
     * <p>爆头带下沿是 {@code 0.80}。此值取在它明显下方，把弹道压在躯干中上段：
     * BadHeadShot 的判定前提就是「怪站在玩家上方」，此时幽灵框是<b>预测位置</b>，
     * 瞄准点越靠上，预测偏差越容易让射线从头顶掠过而打空。普通怪体型小
     * （僵尸 1.95 / 骷髅 1.99），这个效应被放大。
     *
     * <p>历史：Forge 1.8.9 里该系数叫 {@code CHEST_PRIORITY_FRAC}（胸腔优先），
     * 取值 0.76。移植到 Fabric 时被内联成裸数字，语义标签丢失。<b>0.76 对 26.2
     * 的小体型普通怪偏高、实测常打空</b>，故下调为 0.65，并做成配置项便于现场微调。
     *
     * <p>注意：这类怪本就守不住暴击带（瞄准点低于 0.80 时
     * {@code critPitchToleranceDeg} 的 clearance 为负、直接返回 0），因此
     * 提高命中率的收益高于保留爆头机会——值取低更稳。
     */
    public static final double BAD_HEADSHOT_BODY_FRAC_DEFAULT = 0.65;

    /**
     * 巨人的默认瞄准高度系数（作用在幽灵框上）。
     *
     * <p>26.2 巨人尺寸 {@code sized(3.6f, 12.0f).eyeHeight(10.44f)}：瞄 {@code 0.999}
     * 即脚上 <b>11.988 格</b>，比眼高（10.44）高 1.548 格，仍在爆头带
     * {@code [0.80, 1.00]} 之内 —— 距箱顶仅剩 <b>0.012 格</b>，暴击容差极窄，
     * 属用户明确要求的「尽量贴顶」口径（0.98 → 0.995 → 0.999，2026-09-11）。
     *
     * <p>此前巨人复用 {@code 0.9 + 0.2 * Crits}，会被<b>全局 Crits 旋钮连带牵动</b>；
     * 现改用这个专属系数，巨人瞄点与 Crits <b>解耦</b>，也不再受 {@code headFracMax}
     * 夹取（该系数自身范围即 {@code [0.50, 1.00]}）。
     */
    public static final double GIANT_AIM_FRAC_DEFAULT = 0.999;

    /**
     * insta（Insta Kill 秒杀）窗口内的固定瞄准高度系数。
     *
     * <p><b>注意：0.5 是幽灵框高度的中点，即「腰腹」，不是头</b>——爆头带下沿是
     * {@code 0.80}。面板上曾把它标成「固定头点」，那是历史误译：Forge 原版把返回
     * 这个值的函数命名为 {@code usualHeadFrac()}，语义标签沿用至今。
     *
     * <p>设计意图是正确的：秒杀道具生效期间一击必杀，爆头毫无收益，而腰腹是
     * 命中容差最大的位置，所以压到 0.5 换取最高命中率。
     */
    public static final double INSTA_AIM_FRAC = 0.5;

    /**
     * 「降至普通怪之后」档：分组值小于 0 即表示该目标只在首选档<b>无可打目标</b>时才参与。
     *
     * <p>用户定稿 2026-09-11：baby 僵尸（全局，BRUTE 扫射除外）与 insta 窗口内的
     * 史莱姆/岩浆怪都降到这一档 —— 优先打普通怪，只有场上再无别的可打目标时才锁它们。
     */
    public static final int GROUP_DEPRIORITIZED = -1;

    /**
     * 「头顶高处（高度差 &gt; aboveHeightBlocks）」档：与 {@link #GROUP_DEPRIORITIZED}
     * 同属「首选档无可打目标时才参与」的末位档，但排在 baby 之后（两边同时成立时取更小值）。
     *
     * <p>用户定稿 2026-09-14（暴力模式）：高处怪不再整只跳过，改为末位锁定——地面怪
     * 全部不可打时才去锁空中的怪。
     */
    public static final int GROUP_HIGH_ABOVE = -2;

    /**
     * 「巨人末位」档：<b>Clown 模式</b>（{@code prioClown}）打开时的巨人组。
     *
     * <p>用户定稿 2026-09-16：两个优先级开关是各自管一侧的一对——Clown 模式先清小丑、
     * 小怪，巨人不抢优先权，小怪全不可打时才锁它；Giant 模式则把巨人提到
     * {@link #GROUP_PRIORITY}。
     */
    public static final int GROUP_GIANT_BACKUP = -3;

    /** BRUTE 扫射生效时 baby 恢复的最高组。 */
    public static final int GROUP_BABY_FIRST = 2;

    /** 小丑优先组。 */
    public static final int GROUP_PRIORITY = 1;

    /**
     * insta 窗口内的候选分组。
     *
     * <p>用户定稿 2026-09-11：<b>非 baby 且非史莱姆/岩浆怪的怪整体前置</b>。
     * baby 僵尸碰撞箱只有成体一半、移速还快；史莱姆/岩浆怪体型跳脱、命中窗口小。
     * 秒杀期间追求「快速清掉每一只」，所以优先打打得中的，其余降到
     * {@link #GROUP_DEPRIORITIZED} 档。巨人已由调用方在该窗口内整体剔除，这里不再区分。
     *
     * @param babyFirst BRUTE 扫射生效时为 true —— 扫射是「按空间顺序逐个清」，不再降级 baby
     */
    public static int instaGroupRank(boolean baby, boolean slime, boolean babyFirst) {
        if (babyFirst && baby) return GROUP_BABY_FIRST;
        if (baby || slime) return GROUP_DEPRIORITIZED;
        return GROUP_PRIORITY;
    }

    /**
     * 该 PowerUp 名称是否是 Insta Kill。
     *
     * <p>必须大小写无关且用 contains：名称有两个来源——解析器正则会捕获
     * {@code INSTA KILL}（Hypixel 原文全大写），字幕路径则产出 {@code Insta Kill}。
     * 用等值比较会漏掉其中一半。
     */
    public static boolean isInstaKillKind(String kind) {
        if (kind == null || kind.isBlank()) return false;
        return kind.toLowerCase(java.util.Locale.ROOT).contains("insta");
    }

    /** 兜底采样「同样接近首选瞄点」时，对向上采样的微小惩罚，保证优先往下瞄。 */
    public static final double UPWARD_FALLBACK_PENALTY = 1.0e-6;

    /**
     * 首选瞄点不可用时，某个兜底采样的优先分数——<b>越小越优先</b>。
     *
     * <ul>
     *   <li>{@code nearestPreferred = false}（<b>普通怪</b>）：分数取 {@code -frac}，
     *       等价于「箱内最高的可见采样」，维持移植前的既有行为。</li>
     *   <li>{@code nearestPreferred = true}（<b>巨人 / BadHeadShot</b>）：分数取
     *       「与首选瞄点的距离」，因此<b>既允许往下、也允许往上</b>兜底；距离相同时由
     *       {@link #UPWARD_FALLBACK_PENALTY} 让下方采样胜出（下优先、上可用）。</li>
     * </ul>
     *
     * @param sampleFrac       该采样点在幽灵框内的归一化高度
     * @param nearestPreferred 是否按「距首选瞄点最近」挑选
     * @param preferredFrac    首选瞄点系数；{@code nearestPreferred = false} 时忽略
     */
    public static double bodyFallbackScore(double sampleFrac, boolean nearestPreferred,
                                           double preferredFrac) {
        if (!Double.isFinite(sampleFrac)) return Double.POSITIVE_INFINITY;
        if (!nearestPreferred) return -sampleFrac;
        double preferred = Double.isFinite(preferredFrac) ? preferredFrac : 0.5;
        double distance = Math.abs(sampleFrac - preferred);
        return sampleFrac > preferred ? distance + UPWARD_FALLBACK_PENALTY : distance;
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

    /** 爆头带容差的安全系数：不要把准心推到爆头层边界上。 */
    public static final double CRIT_TOL_SAFETY = 0.8;

    /** 爆头带容差的下限（°）：防止远距离角度过小导致永不收敛、持续微抖。 */
    public static final double CRIT_TOL_FLOOR_DEG = 0.2;

    /**
     * 垂直方向仍落在爆头层（碰撞箱上 20%）内的最大 pitch 偏差（°）。
     * 取瞄准点到爆头层上下边界的较小余量换算成角度，再乘安全系数，
     * 保证准心不会越出爆头层 —— 这是「暴击优先」下 pitch 的容差上限。
     * 瞄准点不在爆头层内（例如已降级到身体点）时返回 0，表示无暴击带可守。
     */
    public static double critPitchToleranceDeg(double aimY, double footY, double height,
                                               double horizontalDistance) {
        if (!Double.isFinite(aimY) || !Double.isFinite(footY) || !Double.isFinite(height)) {
            return 0.0;
        }
        if (height <= 0.0 || horizontalDistance <= 0.05) return 0.0;
        double bandBottom = footY + headLayerBottom(height);
        double bandTop = footY + height;
        double clearance = Math.min(aimY - bandBottom, bandTop - aimY);
        if (clearance <= 0.0) return 0.0;
        double raw = CRIT_TOL_SAFETY * Math.toDegrees(Math.atan(clearance / horizontalDistance));
        return Math.max(CRIT_TOL_FLOOR_DEG, raw);
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

    /**
     * 「硬实心」方块名单：属于不可穿透方块 —— 且按「整格」口径遮挡弹道（用户定稿
     * 2026-09-11）：一个方块是 1×1×1 的格，名单方块的<b>整个格子体积</b>都视为挡弹，
     * 不依赖真实碰撞 shape（铁活板门 1×3/16×1 的薄板上方空隙同样挡弹）。路径遍历用
     * {@link #rayCells} 按格序判定 first colliding。
     *
     * <p>理由（用户口径 2026-09-11）：{@code isAllowedFirstSolid} 默认把 {@code *_fence_gate}
     * 与 {@code *_trapdoor} 都视为可穿透，但其中
     * <ul>
     *   <li>{@code oak_fence_gate}（橡木栅栏门）关闭时是一片实心门板，理应挡枪；</li>
     *   <li>{@code iron_trapdoor}（铁活板门）是铁质整格构件，更不该被穿过；</li>
     *   <li>{@code clay}（黏土块）是完整的整格实心方块（0.2.73 起加入），本就不该进任何
     *       穿透白名单 —— 加入它是防御性的：即便后续白名单重构，黏土块也始终挡枪；</li>
     *   <li>{@code *_leaves}（树叶，全木种 + azalea/flowering_azalea，0.2.79 起加入）
     *       为空手/子弹不可穿透的装饰方块，按整格遮挡。</li>
     * </ul>
     * 被误判为可穿透会导致「对着这些方块开枪打空或打到方块后目标」。
     *
     * <p><b>栅栏门/活板门范围严格限定 {@code oak_fence_gate} + {@code iron_trapdoor}</b>：
     * 其余木种的栅栏门（spruce/birch/…）、其余活板门（{@code oak_trapdoor} 等）以及
     * 陶瓦系（{@code *_terracotta}）<b>保持可穿透</b>，与本函数无关。
     * 注意「黏土块」是 {@code clay}，不是黏土球（物品）也不是陶瓦。
     */
    public static boolean isHardSolidPath(String registryPath) {
        if (registryPath == null) return false;
        return "oak_fence_gate".equals(registryPath) || "iron_trapdoor".equals(registryPath)
                || "clay".equals(registryPath) || registryPath.endsWith("_leaves");
    }

    /**
     * DDA（Amanatides &amp; Woo）体素遍历：按<b>路径顺序</b>枚举射线 from→to 经过的所有
     * 整格 cell（含两端所在格），供「不可穿透名单按整格遮挡」的弹道语义使用（用户定稿
     * 2026-09-11）：服务端弹道对名单方块按其所在 1×1×1 格判定接触，不依赖真实碰撞 shape
     * —— 铁活板门 1×3/16×1 的薄板上方空隙同样挡弹；起点格是可穿透方块（站铁栏杆格内）
     * 则整条射线放行。两条规则都要求<b>按格序</b>找 first colliding，故遍历顺序必须严格
     * 沿射线推进。
     *
     * <p>返回 flat {@code int[]}，每格连续三个 int (x,y,z)。恰好穿过格角（两/三轴 tMax
     * 平局）时，各侧 cell 与对角 cell 全部进入结果 —— 整格遮挡宁可多算不可漏判
     * （多判的代价是放弃个别可打点并走兜底，漏判的代价是对着被挡的弹道开枪 MISS）。
     */
    public static int[] rayCells(double x0, double y0, double z0,
                                 double x1, double y1, double z1) {
        int cx = floorCell(x0);
        int cy = floorCell(y0);
        int cz = floorCell(z0);
        int ex = floorCell(x1);
        int ey = floorCell(y1);
        int ez = floorCell(z1);
        // 记录次数上界 = 各轴跨界数之和 + 起点 1（平局多轴同推只是把多轴的跨界合到一步，
        // 总记录数不变），再留 2 格余量。
        int cap = Math.abs(ex - cx) + Math.abs(ey - cy) + Math.abs(ez - cz) + 3;
        int[] out = new int[cap * 3];
        int n = 0;
        out[n++] = cx;
        out[n++] = cy;
        out[n++] = cz;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        int sx = dx >= 0 ? 1 : -1;
        int sy = dy >= 0 ? 1 : -1;
        int sz = dz >= 0 ? 1 : -1;
        double tMaxX = boundaryDist(x0, dx, cx);
        double tMaxY = boundaryDist(y0, dy, cy);
        double tMaxZ = boundaryDist(z0, dz, cz);
        double tDX = dx == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDY = dy == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDZ = dz == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        final double eps = 1.0E-9;
        while ((cx != ex || cy != ey || cz != ez) && n + 3 <= out.length) {
            double m = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            boolean advanced = false;
            // 平局多轴同推：侧格与对角格依次全部记录（格角两侧都算被接触）。
            if (tMaxX <= m + eps && cx != ex) {
                cx += sx;
                tMaxX = cx == ex ? Double.POSITIVE_INFINITY : tMaxX + tDX;
                advanced = true;
                out[n++] = cx;
                out[n++] = cy;
                out[n++] = cz;
            }
            if (tMaxY <= m + eps && cy != ey) {
                cy += sy;
                tMaxY = cy == ey ? Double.POSITIVE_INFINITY : tMaxY + tDY;
                advanced = true;
                out[n++] = cx;
                out[n++] = cy;
                out[n++] = cz;
            }
            if (tMaxZ <= m + eps && cz != ez) {
                cz += sz;
                tMaxZ = cz == ez ? Double.POSITIVE_INFINITY : tMaxZ + tDZ;
                advanced = true;
                out[n++] = cx;
                out[n++] = cy;
                out[n++] = cz;
            }
            // 浮点兜底：理论上每次循环必有至少一轴推进；真的没有就停，避免死循环。
            if (!advanced) break;
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    /** 射线所在轴上、从坐标 {@code pos} 到下一条格边界（沿 {@code dir} 方向）的格距离。 */
    private static double boundaryDist(double pos, double dir, int cell) {
        if (dir == 0.0) return Double.POSITIVE_INFINITY;
        double next = dir > 0 ? cell + 1.0 : cell;
        return Math.abs(next - pos) / Math.abs(dir);
    }

    private static int floorCell(double v) {
        return (int) Math.floor(v);
    }

    /**
     * 水平无级兜底 —— 箱缘采样向内收的保持系数。
     *
     * <p>用户定稿 2026-09-11：扫描点原先<b>永远在幽灵框 X/Z 中线</b>，中线被柱子/窗框/栅栏
     * 挡住时整只怪会被判「不可打」丢弃 —— 即便左右边缘完全可见。改为「垂线滑动 + 二分逼近」
     * 的无级兜底后，只要碰撞箱在该高度层<b>有任一暴露面</b>就能找到可打点。
     *
     * <p>二分中「可见侧」的初始端点取在 {@code slideHalfExtent × HORIZONTAL_SCAN_KEEP}
     * 处：<b>不能取 1.0（贴边）</b> —— 采样点落在箱面上时，视线与碰撞箱的相交判定可能因
     * 浮点精度 MISS；向内收 5% 保证端点必在箱内。
     */
    public static final double HORIZONTAL_SCAN_KEEP = 0.95;

    /** 中线被挡且箱缘可见时的二分步数：分辨率 = 可见半程 / 2^steps（约 6% 半程）。 */
    public static final int HORIZONTAL_BISECT_STEPS = 4;

    /**
     * 瞄准点竖扫步长（格，绝对值）。移植 OceanClient 的 {@code Y_RES = 0.05}。
     *
     * <p>用户定稿 2026-09-15：原先按「箱子高度的百分比」分 12 层（僵尸身上约 0.195 格/步），
     * 一条 0.1 格高的可见缝会被整层跳过；改成绝对 0.05 格后不漏缝，且「自上而下首个可见
     * 即停」的语义让常态射线数反而更少（首选点可见时只打一发）。
     */
    public static final double AIM_SCAN_STEP = 0.05;

    /** 竖扫层数上限：整只怪被完全遮挡时的射线数护栏（0.05 步长够扫 2.4 格）。 */
    public static final int AIM_SCAN_MAX_LAYERS = 48;

    /**
     * 单只目标每 tick 允许做几次「水平兜底」（垂线 ±45° 三方向 + 二分，最贵 18 发射线）。
     * 遮挡带一般都在高处，前几次兜底覆盖后，更低的层只试中线——把最坏情况钉在预算内。
     */
    public static final int AIM_FALLBACK_BUDGET = 4;

    /**
     * 水平兜底方向数：视线垂线、垂线向视线方向转 ±45°。
     *
     * <p>只用垂线时，遮挡带的走向若与垂线平行（斜放的方块、墙角）就会整条被挡；
     * 加上左右 45° 后覆盖三个方向（用户 2026-09-15 定稿）。
     */
    public static final int HORIZONTAL_DIRECTIONS = 3;

    /**
     * 轴对齐盒的支撑函数：箱体半宽 {@code (hx, hz)} 沿单位滑动方向 {@code (nx, nz)}
     * 的最大投影长度。中线沿 n 滑动 {@code ±slideHalfExtent} 恰好滑到箱缘。
     */
    public static double slideHalfExtent(double hx, double hz, double nx, double nz) {
        return hx * Math.abs(nx) + hz * Math.abs(nz);
    }

    /**
     * 生成水平兜底方向表（3 个，已归一化）：{@code [垂线, 垂线+45°, 垂线-45°]}。
     *
     * <p>调用方保证 {@code (nx, nz)} 是单位向量、且 {@code (vx, vz)} 是与之垂直的单位
     * 视线方向——垂线绕水平面转 ±45° 即 {@code (n ± v) / √2}，无需三角函数。
     */
    public static double[] fallbackDirections(double nx, double nz, double vx, double vz) {
        double k = Math.sqrt(0.5);
        return new double[]{
                nx, nz,
                (nx + vx) * k, (nz + vz) * k,
                (nx - vx) * k, (nz - vz) * k,
        };
    }

    /**
     * 竖扫共有几层：从 {@code fromY} 起每 {@code step} 往下一层，直到 {@code minY}（含），
     * 最多 {@code maxLayers} 层。
     */
    public static int scanLayerCount(double fromY, double minY, double step, int maxLayers) {
        if (!(step > 0.0) || maxLayers <= 0 || !(fromY >= minY)) return fromY < minY ? 0 : 1;
        double span = fromY - minY;
        // 1e-9 的容差：1.80/0.05 在浮点下是 35.999…，不补这一下会少一层（比如正好贴着箱底）
        int count = (int) Math.floor(span / step + 1.0e-9) + 1;
        return Math.max(1, Math.min(maxLayers, count));
    }

    /** 竖扫第 {@code index} 层的高度（index 从 0 起，沿 y 向下）。 */
    public static double scanLayerY(double fromY, double minY, int index, double step) {
        if (index <= 0) return fromY;
        return Math.max(minY, fromY - step * index);
    }

    /**
     * 向上兜底共有几层：从 {@code fromY} 起每 {@code step} 往上一层，直到 {@code maxY}。
     * index 从 1 起（第 1 层 = {@code fromY + step}），故层数不含起点本身。
     */
    public static int upwardLayerCount(double fromY, double maxY, double step, int maxLayers) {
        if (!(step > 0.0) || maxLayers <= 0 || !(maxY > fromY)) return 0;
        int count = (int) Math.floor((maxY - fromY) / step);
        return Math.max(0, Math.min(maxLayers, count));
    }

    /** 向上兜底第 {@code index} 层的高度（index 从 1 起，沿 y 向上，封顶 maxY）。 */
    public static double upwardLayerY(double fromY, double maxY, int index, double step) {
        if (index <= 0) return fromY;
        return Math.min(maxY, fromY + step * index);
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

    /* ---- 暴力模式扫射（BRUTE sweep，2026-09-10 用户定稿）----
     * 与 Humanize 的连续扫射不同：不做连续扫描线，而是在限定 FOV 内
     * 「逐个精准锁定 + 超快速切换」，快速扫过一堆怪里的每一个目标。 */

    /**
     * 扫射回合门控：`round <= 0`（回合未知，例如非 Zombies 局）不门控，
     * 与 0.2.66 的 Humanize 扫射门控保持同一口径。
     */
    public static boolean bruteSweepAllowed(int round, int minRound) {
        if (round <= 0) return true;
        return round >= Math.max(1, minRound);
    }

    /**
     * 扫射锥体是否已「过期」——过期就该重锚到当前准星（0.2.99，用户定稿 2026-09-15）。
     *
     * <p>锚定不漂移只解决「扫射自己带偏准星导致锥体跟着漂」；它同时带来另一个后果：
     * 玩家自己转身 / 换波之后，老角度里可能只剩 0~1 只怪，而只有 1 只时游标只有一个落点
     * —— 表现就是「锁单只，完全不扫」。两种过期情形：
     * <ul>
     *   <li>{@code coneCount < minInCone}：锥内目标不够扫（0 只退化单锁、1 只无从推进）；</li>
     *   <li>准星跑出锥体（{@code crosshairOffDeg > fovDeg}）：玩家已经刻意看向别处，
     *       老锥体不再代表他想打的方向。</li>
     * </ul>
     * 只要满足其一即视为过期；调用方用「持续这么久才动手」的滞回来过滤甩枪抖动。
     */
    public static boolean bruteSweepConeStale(int coneCount, int minInCone,
                                              double crosshairOffDeg, double fovDeg) {
        if (coneCount < Math.max(1, minInCone)) return true;
        return Double.isFinite(crosshairOffDeg) && crosshairOffDeg > Math.max(0.0, fovDeg);
    }

    /** 只有落在限定 FOV 半角内的目标才参与暴力扫射（不做 360° 乱扫）。 */
    public static boolean bruteSweepInFov(double signedYawDeg, double fovDeg) {
        if (!Double.isFinite(signedYawDeg) || !Double.isFinite(fovDeg)) return false;
        return Math.abs(signedYawDeg) <= Math.abs(fovDeg);
    }

    /**
     * 空间顺序推进：给出按 signed yaw 升序排列的候选偏角，返回第一个
     * 严格大于 `prevYawOff + eps` 的下标；若已扫到最右端则回到 0（重新从左开始）。
     * `prevYawOff` 不是有限值时也从 0 开始。
     *
     * @return 下标；数组为空返回 -1
     */
    public static int bruteSweepAdvance(double[] yawOffAscending, double prevYawOff, double eps) {
        if (yawOffAscending == null || yawOffAscending.length == 0) return -1;
        double start = Double.isFinite(prevYawOff) ? prevYawOff + Math.max(0.0, eps) : Double.NEGATIVE_INFINITY;
        for (int i = 0; i < yawOffAscending.length; i++) {
            if (yawOffAscending[i] > start) return i;
        }
        return 0;
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

    /**
     * Caps one render-frame write to what is left of the queued controller
     * delta. Needed once the consumption window can be shorter than one
     * controller tick: without the cap the same delta would be re-applied
     * every 50 ms and the camera would fly past the decided angle.
     *
     * <p>Returns 0 when either side is non-finite, when nothing is left, or
     * when the two disagree in sign (the queue was refreshed mid-drain).
     */
    public static double limitToRemaining(double step, double remaining) {
        if (!Double.isFinite(step) || !Double.isFinite(remaining)) return 0.0;
        if (step == 0.0 || remaining == 0.0) return 0.0;
        if (Math.signum(step) != Math.signum(remaining)) return 0.0;
        return Math.abs(step) <= Math.abs(remaining) ? step : remaining;
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

    /**
     * 普通（非 insta）分组。
     *
     * <p>用户定稿 2026-09-11：<b>废弃 Prio Baby 开关</b>——baby 僵尸默认降到
     * {@link #GROUP_DEPRIORITIZED}（排在普通怪之后，只有再无别的可打目标时才锁），
     * 仅当 BRUTE 扫射生效（{@code babyFirst}）时恢复 {@link #GROUP_BABY_FIRST} 最高组。
     *
     * <p>用户定稿 2026-09-16：两个优先级开关是<b>对称的一对</b>，各自把自己的目标提进
     * 首选档、需要时把对方压下去（配置层保证二者互斥）：
     * <ul>
     *   <li>{@code prioClown}（Clown 模式）：小丑进 {@link #GROUP_PRIORITY}，
     *       巨人降到 {@link #GROUP_GIANT_BACKUP}——先清小丑小怪，只剩巨人才锁它。</li>
     *   <li>{@code prioGiant}（Giant 模式）：巨人进 {@link #GROUP_PRIORITY}，优先锁巨人。</li>
     * </ul>
     *
     * <p>这里修掉 2026-09-14 的错挂：当时把巨人降档挂在 {@code prioGiant} 上，于是
     * Clown 模式没人压得住巨人（留在组 0，靠同组内 TOO/巨人优先排序抢靶），
     * Giant 模式反倒把巨人踢出首选池（{@code group < 0} 进降级池，普通怪能打就永远不锁）。
     */
    public static int groupRank(boolean prioClown, boolean prioGiant, boolean babyFirst,
                                boolean baby, boolean clown, boolean giant) {
        if (babyFirst && baby) return GROUP_BABY_FIRST;
        if (prioGiant && giant) return GROUP_PRIORITY;
        if (prioClown && clown) return GROUP_PRIORITY;
        if (prioClown && giant) return GROUP_GIANT_BACKUP;
        return baby ? GROUP_DEPRIORITIZED : 0;
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

    /**
     * 「忽略头顶高处」的豁免回合：<b>第 21 回合</b>。
     *
     * <p>用户定稿 2026-09-11：R21 是 Prison 的 5 波长表回合，飞碟/高处投放的怪密集，
     * 原规则把它们全部跳过会导致 R21 无人可打，故<b>仅该回合</b>豁免——允许打这些
     * 头顶高处的怪；其余回合照常跳过（含 {@code ignoreMidFall} 与
     * {@code ignoreVerticalFall} 两条不受影响）。回合未知（≤0）不豁免。
     *
     * <p>注意豁免的是「Ignored Above」这一条规则本身，阈值 {@code aboveHeightBlocks}
     * 保持 5.0 不变。
     */
    public static final int ABOVE_HEIGHT_EXEMPT_ROUND = 21;

    public static boolean aboveHeightExemptRound(int round) {
        return round == ABOVE_HEIGHT_EXEMPT_ROUND;
    }

    public static boolean isHighSpeedFall(double y, double motionY, double horizontalSpeed,
                                          double groundY, double fallSpeed, double maxHorizontal) {
        if (!Double.isFinite(y) || !Double.isFinite(motionY)) return false;
        if (y <= groundY) return false;
        if (motionY >= -Math.abs(fallSpeed)) return false;
        return Math.abs(horizontalSpeed) <= Math.abs(maxHorizontal);
    }

    /* ==================== BRUTE 扫射状态机（纯逻辑，可离线回归） ====================
     *
     * 扫射 = 「沿锥体从左到右逐个清」：锁定一只 → 打死 → 顺路推进到下一只 → …
     * 这里把「保持 / 推进」的决策与状态迁移抽成纯函数，避免再出现
     * 「在游戏里才能发现扫射其实没工作」的情况（2026-09-14 连续两次实测回归）。
     */

    /** 扫射选择结果：选中的目标 + 需要写回的状态。 */
    public record BruteSweepPick(int entityId, double yawOff, long holdUntilMs, boolean advanced) { }

    /** 空决策（锥体内没有可打目标；调用方应回落单锁）。 */
    public static final BruteSweepPick BRUTE_SWEEP_NONE = new BruteSweepPick(-1, Double.NaN, 0L, false);

    /** 停留保护：不限时长（只按「目标死亡 / 移出锥体」推进）。 */
    public static final long BRUTE_HOLD_FOREVER = Long.MAX_VALUE;

    /**
     * 从「已按 signed yaw 升序排好的锥内候选」里选出本次锁定的目标。
     *
     * <p>语义（用户定稿 2026-09-15）：<b>每只最多停留 {@code dwellMs} 就立刻切下一只，
     * 不管有没有打死</b>——扫射的本体就是这个上限，默认 100ms。{@code dwellMs <= 0}
     * 的「只按死亡/离锥推进、不设上限」分支仍被保留（纯函数兜底与离线回归用），
     * 但配置层已把它夹到 40–600，游戏里不可达。
     *
     * @param ids          锥内候选实体 id（与 yawOff 同序）
     * @param yawOff       对应的 signed yaw 偏角（升序）
     * @param heldId       当前锁定的实体 id，-1 表示无
     * @param prevYawOff   上一次落点的偏角（NaN = 尚未落点）
     * @param nowMs        当前时间
     * @param holdUntilMs  当前目标的停留截止（{@link #BRUTE_HOLD_FOREVER} = 不限）
     * @param dwellMs      停留上限（毫秒）；{@code <= 0} 表示不设上限
     * @param eps          推进容差（度）
     */
    public static BruteSweepPick bruteSweepDecision(int[] ids, double[] yawOff,
                                                    int heldId, double prevYawOff,
                                                    long nowMs, long holdUntilMs,
                                                    int dwellMs, double eps) {
        if (ids == null || yawOff == null || ids.length == 0 || ids.length != yawOff.length) {
            return BRUTE_SWEEP_NONE;
        }
        long limit = dwellMs <= 0 ? BRUTE_HOLD_FOREVER : nowMs + dwellMs;
        // 保持：当前目标仍在锥内、且停留保护未到期 → 继续锁它（不换目标 = 不停顿）
        if (heldId >= 0 && nowMs < holdUntilMs) {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == heldId) {
                    return new BruteSweepPick(heldId, yawOff[i], holdUntilMs, false);
                }
            }
            // 目标已不在锥内（死亡 / 移出 / 被挡）→ 立刻推进，不等停留保护到期
        }
        // 推进：bruteSweepAdvance 内部 +eps，保证不会重新选回同一只
        //（选回同一只 = 扫射退化成单锁，用户 2026-09-14 实测回归）
        int pos = bruteSweepAdvance(yawOff, prevYawOff, eps);
        if (pos < 0) pos = 0;
        return new BruteSweepPick(ids[pos], yawOff[pos], limit, true);
    }

    /**
     * 生成锚定锥体内的候选偏角（升序），返回排序后的下标。
     *
     * @param anchorYaw  激活瞬间锁定的锥体中心 yaw（不是当前准星——相对当前准星判定会让
     *                   锥体被扫射带着漂移，实际能扫到全图）
     * @param fovDeg     FOV 半角（度）
     * @param targetYaw  各可打目标的瞄点 yaw（未过滤）
     * @param outOffsets 输出用缓冲，长度必须 ≥ {@code targetYaw.length}；前 count 项为升序偏角
     * @return 升序排列的 yaw 偏角数组下标（与 outOffsets 同序）；无候选返回空数组
     */
    public static int[] bruteSweepConeSlots(double anchorYaw, double fovDeg,
                                            double[] targetYaw, double[] outOffsets) {
        if (targetYaw == null || targetYaw.length == 0) return new int[0];
        int[] slots = new int[targetYaw.length];
        int count = 0;
        for (int i = 0; i < targetYaw.length; i++) {
            double off = angleDelta(anchorYaw, targetYaw[i]);
            if (!bruteSweepInFov(off, fovDeg)) continue;
            int k = count++;
            while (k > 0 && outOffsets[k - 1] > off) {
                outOffsets[k] = outOffsets[k - 1];
                slots[k] = slots[k - 1];
                k--;
            }
            outOffsets[k] = off;
            slots[k] = i;
        }
        return java.util.Arrays.copyOf(slots, count);
    }
}

package dev.micx.micxfabric;

/**
 * P4 刷怪点周边的「不可穿透」判定（用户定稿 2026-09-16）。
 *
 * <p>以 P4 传送点为中心、半径 8 格的球内，<b>云杉木楼梯</b>（{@code spruce_stairs}）
 * 一律按不可穿透处理 —— 而且必须是「立即挡枪的实心格」，不能只是「不在穿透白名单里」。
 *
 * <p>为什么必须区分这两种：{@code canWallShot} 沿射线逐格扫描，
 * 「不在白名单」的格子只是被略过、不中断扫描；同一格序上若还有可穿透格
 * （半砖 / 玻璃 / 铁栏杆 / 栅栏门…），整条射线仍会被判为可打 —— 于是对着 P4 那圈
 * 云杉木楼梯开枪会被放行，实际打空。云杉木楼梯本来就不在穿透白名单里
 * （{@code AimbotModule.isAllowedFirstSolid} 对木楼梯直接返回 false），
 * 缺的正是「立即挡枪」这一层。
 */
public final class SpawnWallRules {
    /** P4 传送点（AA 真刷怪点 -10.5,-5.5）。与 {@link SpawnMarkerModule} 的 p4 标记同源。 */
    public static final double P4_X = -10.0;
    public static final double P4_Y = 72.0;
    public static final double P4_Z = -6.0;
    /** 判定半径（格）：球心 P4，球面 8 格。 */
    public static final double P4_STAIR_RADIUS = 8.0;

    private SpawnWallRules() {
    }

    /**
     * 该方块是否是「P4 周边的云杉木楼梯」（是 → 弹道按不可穿透的实心格处理）。
     *
     * <p>距离用<b>方块中心</b>（格坐标 +0.5）到 P4 的 3D 距离，半径含边界（{@code <= 8}）。
     *
     * @param registryPath 方块注册名路径（如 {@code spruce_stairs}）
     */
    public static boolean isP4ImpenetrableStair(String registryPath,
                                                double blockX, double blockY, double blockZ) {
        if (!"spruce_stairs".equals(registryPath)) return false;
        double dx = blockX + 0.5 - P4_X;
        double dy = blockY + 0.5 - P4_Y;
        double dz = blockZ + 0.5 - P4_Z;
        return dx * dx + dy * dy + dz * dz <= P4_STAIR_RADIUS * P4_STAIR_RADIUS;
    }
}

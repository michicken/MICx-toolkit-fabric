package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 窗口刷怪量出生点归档规则。
 *
 * <p>现行口径（用户定版）：有效刷怪 = 3×3 方块（中心 ±1.5 格，含边界），
 * y = 该窗地面层 ±3.0，且 y 必须落在<b>精确整数层</b>（容差 0.1）。
 *
 * <p>三条防污染规则各自有回归锁：
 * <ul>
 *   <li>{@link #practiceYLayerIsRejected()} —— 练习区刷在 72.8/73.8/74.8，靠整数层闸门拦；</li>
 *   <li>{@link #perWindowGroundYIsHonored()} —— BR/BL 地面在 y=73，逐窗配置；</li>
 *   <li>{@link #p4TrueSpawnPointIsArchived()} —— P4 真刷怪点 (-10.5,-5.5,72.0)。</li>
 * </ul>
 */
class WindowSpawnCounterRulesTest {

    private static final double Y = 72.0;

    private static int indexOf(String id) {
        List<WindowSpawnCounterModule.WindowDef> windows = WindowSpawnCounterModule.WINDOWS;
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).id.equals(id)) return i;
        }
        throw new IllegalArgumentException(id);
    }

    @Test
    void spawnAtWindowCenterIsArchivedToThatWindow() {
        // P1 = (6, 32)
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(6.0, Y, 32.0));
        // ULT = (28, 32)
        assertEquals(indexOf("ULT"), WindowSpawnCounterModule.windowIndexForBirth(28.0, Y, 32.0));
        // BL = (34, -2)，地面 y=73
        assertEquals(indexOf("BL"), WindowSpawnCounterModule.windowIndexForBirth(34.0, 73.0, -2.0));
    }

    @Test
    void spawnInsideThreeByThreeBoxIsArchived() {
        // 3×3 边界含 ±1.5：对角极限 (1.5, 1.5)
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(7.5, Y, 33.5));
        // 单轴偏移 1.5
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(7.5, Y, 32.0));
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(6.0, Y, 33.5));
        // 真实散布点（刷怪点通常落在半格上）
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(6.5, Y, 31.5));
    }

    @Test
    void spawnOutsideThreeByThreeBoxIsNotCounted() {
        // 单轴刚出框：1.5 + 0.05
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(7.55, Y, 32.0));
        // 对角出框
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(7.6, Y, 33.6));
        // 远离所有窗
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(11.0, Y, 32.0));
    }

    @Test
    void spawnBeyondVerticalToleranceIsNotCounted() {
        // y 容差 ±3.0（P1 地面 72）
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(6.0, Y + 5.0, 32.0));
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(6.0, Y - 5.0, 32.0));
        // 容差边界内仍归档（整数层）
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(6.0, Y + 3.0, 32.0));
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(6.0, Y - 3.0, 32.0));
        // P1 实测第二刷怪点 y=69（1,488 只真怪），必须能归档
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(6.5, 69.0, 31.5));
    }

    @Test
    void ambiguousPointBetweenTwoWindowsGoesToNearest() {
        // P2 = (-22, 16) 与 P3 = (-22, 10)，相距 6.0，中点为 (-22, 13)。
        // 3×3 只覆盖 z∈[14.5,17.5] 与 z∈[8.5,11.5]，故取框内靠中间的点验证最近窗归属。
        assertEquals(indexOf("P2"), WindowSpawnCounterModule.windowIndexForBirth(-22.0, Y, 14.6));
        assertEquals(indexOf("P3"), WindowSpawnCounterModule.windowIndexForBirth(-22.0, Y, 11.4));
        // 中点附近（两框之间的真空带）不归档
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(-22.0, Y, 13.0));
    }

    @Test
    void nearestWindowIndexAgreesWithArchiveRule() {
        List<WindowSpawnCounterModule.WindowDef> windows = WindowSpawnCounterModule.WINDOWS;
        for (WindowSpawnCounterModule.WindowDef w : windows) {
            // 每个窗中心偏移 1 格（3×3 内），最近窗必须是自己
            assertEquals(indexOf(w.id), WindowSpawnCounterModule.windowIndexForBirth(w.x + 1.0, w.y, w.z),
                    "window " + w.id + " must claim a spawn 1 block away");
        }
    }

    /**
     * 练习区防污染核心锁：练习区刷在 y=72.8 / 73.8 / 74.8（974 局共 183,754 只，
     * 配比恒定 鸡:狼:牛 = 2:1:1，其中 46,220 只是狼，isCountable 拦不住）。
     * 整数层闸门容差必须取 0.1 —— 72.8 距最近整数仅 0.2，用 0.5 会被判成「接近整数」放行。
     */
    @Test
    void practiceYLayerIsRejected() {
        double[] practiceY = {72.8, 73.8, 74.8};
        // 练习区 9 个 XZ 点：横排 z=-0.5 (x -16.5..-12.5)，竖列 x=-16.5 (z 0.5..3.5)
        double[][] practiceXZ = {
                {-16.5, -0.5}, {-15.5, -0.5}, {-14.5, -0.5}, {-13.5, -0.5}, {-12.5, -0.5},
                {-16.5, 0.5}, {-16.5, 1.5}, {-16.5, 2.5}, {-16.5, 3.5},
        };
        for (double y : practiceY) {
            for (double[] p : practiceXZ) {
                assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(p[0], y, p[1]),
                        "practice spawn at (" + p[0] + ", " + y + ", " + p[1] + ") must not be archived");
            }
        }
    }

    /** BR / BL 的地面在 y=73.0，不是 72.0 —— 逐窗 y 配置，收紧容差时不会归零。 */
    @Test
    void perWindowGroundYIsHonored() {
        assertEquals(indexOf("BR"), WindowSpawnCounterModule.windowIndexForBirth(22.0, 73.0, -14.0));
        assertEquals(indexOf("BL"), WindowSpawnCounterModule.windowIndexForBirth(34.0, 73.0, -2.0));
        // 主区 9 窗仍在 72.0
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(6.0, 72.0, 32.0));
        assertEquals(indexOf("P4"), WindowSpawnCounterModule.windowIndexForBirth(-10.0, 72.0, -6.0));
    }

    /**
     * P4 真刷怪点回归锁。974 局全量实测：唯一刷怪点 (-10.5,-5.5,72.0)，49,568 只，
     * 100% 落在 y=72.0，练习区污染 0 只。此前误改到 (-14,-0.5) 时，20,444 只里
     * 20,440 只是练习区的狼，真怪只剩 4 只。
     */
    @Test
    void p4TrueSpawnPointIsArchived() {
        assertEquals(indexOf("P4"), WindowSpawnCounterModule.windowIndexForBirth(-10.5, 72.0, -5.5));
        // 练习区最近点 (-12.5,-0.5) 到 P4 中心 6.04 格，不得被 P4 认领
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(-12.5, 72.0, -0.5));
    }

    @Test
    void boxHalfStaysBelowClosestWindowSpacing() {
        // 11 窗最近间距 6.00 格（P2↔P3）。3×3 半宽 1.5 远小于该间距的一半，
        // 保证任一窗的归档邻域不会吞掉邻近窗，归属始终由最近距离决定。
        List<WindowSpawnCounterModule.WindowDef> windows = WindowSpawnCounterModule.WINDOWS;
        assertEquals(11, windows.size(), "expected 11 ground windows");
        double minSpacing = Double.MAX_VALUE;
        for (int i = 0; i < windows.size(); i++) {
            WindowSpawnCounterModule.WindowDef a = windows.get(i);
            for (int j = i + 1; j < windows.size(); j++) {
                WindowSpawnCounterModule.WindowDef b = windows.get(j);
                minSpacing = Math.min(minSpacing, Math.hypot(a.x - b.x, a.z - b.z));
            }
        }
        assertEquals(6.0, minSpacing, 0.001, "closest window spacing (P2<->P3)");
        assertTrue(1.5 * 2 < minSpacing,
                "3x3 box width must stay below the closest window spacing");
    }

    // ——— 怪驱动切段（0.2.64）：表时刻只作闸门，怪没来不清零 ———

    /** 出生切段闸门：下一波 nominal −1.5s 之前出生的怪仍归当前波，越过后归下一波。 */
    @Test
    void birthFlipWaitsForGate() {
        int[] times = {10, 22, 34};
        assertEquals(1, WindowSpawnCounterModule.flipTargetOnBirth(1, 8500, times));
        assertEquals(2, WindowSpawnCounterModule.flipTargetOnBirth(1, 20600, times));
        assertEquals(2, WindowSpawnCounterModule.flipTargetOnBirth(1, 32499, times));
        assertEquals(3, WindowSpawnCounterModule.flipTargetOnBirth(1, 32500, times));
    }

    /** 切段封顶总波数：进入最后一波后 flip 不再推进（数量保留到回合结束的核心保障）。 */
    @Test
    void birthFlipNeverPassesLastWave() {
        int[] times = {10, 22, 34};
        assertEquals(3, WindowSpawnCounterModule.flipTargetOnBirth(3, 999999, times));
        // 单波回合（r101-105 = {5}）
        assertEquals(1, WindowSpawnCounterModule.flipTargetOnBirth(1, 999999, new int[]{5}));
    }

    /** 静默兜底：下一波 nominal +6s 仍无怪才推进；同样封顶最后一波。 */
    @Test
    void forceAdvanceIsNominalPlusGrace() {
        int[] times = {10, 22, 34};
        assertEquals(1, WindowSpawnCounterModule.forceTarget(1, 27999, times));
        assertEquals(2, WindowSpawnCounterModule.forceTarget(1, 28000, times));
        assertEquals(3, WindowSpawnCounterModule.forceTarget(2, 40000, times));
        assertEquals(3, WindowSpawnCounterModule.forceTarget(3, 999999, times));
    }
}

package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 窗口刷怪量出生点归档规则。
 *
 * <p>背景：0.2.58 起 HUD 用「正方形 3×3（±1.5 格）」严格命中判定，与类文档的
 * 「最近窗归档」口径不符，实测刷怪散布远超 ±1.5 格导致漏计。现改为最近窗 + 归档半径。
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
        // BL = (34, -2)
        assertEquals(indexOf("BL"), WindowSpawnCounterModule.windowIndexForBirth(34.0, Y, -2.0));
    }

    @Test
    void spawnOffsetBeyondOldThreeByThreeBoxIsStillArchived() {
        // 旧逻辑要求 |dx|<=1.5 && |dz|<=1.5，对角偏移 1.6 即漏计；新逻辑按最近窗半径 4.0 归档
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(7.6, Y, 33.6));
        // 单轴偏移 3.0：旧逻辑完全漏计
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(9.0, Y, 32.0));
        // 半径内的对角极限：偏移 (2.8, 2.8) 距离约 3.96
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(8.8, Y, 34.8));
    }

    @Test
    void spawnOutsideArchiveRadiusIsNotCounted() {
        // P1 外 5 格，且远离其它窗
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(11.0, Y, 32.0));
        // 半径边界外一点点
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(10.1, Y, 32.0));
    }

    @Test
    void spawnBeyondVerticalToleranceIsNotCounted() {
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(6.0, Y + 5.0, 32.0));
        assertEquals(-1, WindowSpawnCounterModule.windowIndexForBirth(6.0, Y - 5.0, 32.0));
        // 容差内仍归档
        assertEquals(indexOf("P1"), WindowSpawnCounterModule.windowIndexForBirth(6.0, Y + 3.5, 32.0));
    }

    @Test
    void ambiguousPointBetweenTwoWindowsGoesToNearest() {
        // P2 = (-22, 16) 与 P3 = (-22, 10)，相距 6.0，中点为 (-22, 13)
        assertEquals(indexOf("P2"), WindowSpawnCounterModule.windowIndexForBirth(-22.0, Y, 13.5));
        assertEquals(indexOf("P3"), WindowSpawnCounterModule.windowIndexForBirth(-22.0, Y, 12.5));
    }

    @Test
    void nearestWindowIndexAgreesWithArchiveRule() {
        List<WindowSpawnCounterModule.WindowDef> windows = WindowSpawnCounterModule.WINDOWS;
        for (WindowSpawnCounterModule.WindowDef w : windows) {
            // 每个窗中心偏移 2 格，最近窗必须是自己
            assertEquals(indexOf(w.id), WindowSpawnCounterModule.windowIndexForBirth(w.x + 2.0, Y, w.z),
                    "window " + w.id + " must claim a spawn 2 blocks away");
        }
    }

    @Test
    void archiveRadiusStaysBelowClosestWindowSpacing() {
        // 11 窗最近间距 6.00 格（P2↔P3）。半径 4.0 严格小于该间距，
        // 保证任一窗的归档邻域不会吞掉邻近窗的中心，归属始终由最近距离决定。
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
        assertTrue(4.0 < minSpacing,
                "archive radius must stay below the closest window spacing");
    }
}

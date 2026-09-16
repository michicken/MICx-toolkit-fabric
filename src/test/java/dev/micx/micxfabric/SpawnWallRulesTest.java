package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4 半径 8 格内的云杉木楼梯 = 不可穿透（用户定稿 2026-09-16）。 */
class SpawnWallRulesTest {
    @Test
    void p4AnchorMatchesTheSpawnMarkerTable() {
        // SpawnMarkerModule.PRESET_SPAWNS 的 p4 项就是这三个常量（单一真源）
        assertEquals(-10.0, SpawnWallRules.P4_X);
        assertEquals(72.0, SpawnWallRules.P4_Y);
        assertEquals(-6.0, SpawnWallRules.P4_Z);
        assertEquals(8.0, SpawnWallRules.P4_STAIR_RADIUS);
    }

    @Test
    void spruceStairsInsideTheRadiusAreImpenetrable() {
        // P4 自己那一格（中心距球心 ≈0.87 格）
        assertTrue(SpawnWallRules.isP4ImpenetrableStair("spruce_stairs", -10, 72, -6));
        // 水平 7 格（中心距 ≈7.53 ≤ 8）
        assertTrue(SpawnWallRules.isP4ImpenetrableStair("spruce_stairs", -18, 72, -6));
        // 竖直方向也算：P4 上方 6 格
        assertTrue(SpawnWallRules.isP4ImpenetrableStair("spruce_stairs", -10, 78, -6));
    }

    @Test
    void spruceStairsOutsideTheRadiusAreNotAffected() {
        // 中心距 8.53 > 8
        assertFalse(SpawnWallRules.isP4ImpenetrableStair("spruce_stairs", -19, 72, -6));
        assertFalse(SpawnWallRules.isP4ImpenetrableStair("spruce_stairs", 0, 72, -6));
        assertFalse(SpawnWallRules.isP4ImpenetrableStair("spruce_stairs", -10, 81, -6));
    }

    @Test
    void onlySpruceStairsAreMatched() {
        assertFalse(SpawnWallRules.isP4ImpenetrableStair("oak_stairs", -10, 72, -6));
        assertFalse(SpawnWallRules.isP4ImpenetrableStair("stone_stairs", -10, 72, -6));
        assertFalse(SpawnWallRules.isP4ImpenetrableStair("spruce_planks", -10, 72, -6));
        assertFalse(SpawnWallRules.isP4ImpenetrableStair("spruce_slab", -10, 72, -6));
        assertFalse(SpawnWallRules.isP4ImpenetrableStair("air", -10, 72, -6));
        assertFalse(SpawnWallRules.isP4ImpenetrableStair(null, -10, 72, -6));
        // 名字必须完全相等，不能被前缀骗过
        assertFalse(SpawnWallRules.isP4ImpenetrableStair("stripped_spruce_stairs", -10, 72, -6));
    }
}

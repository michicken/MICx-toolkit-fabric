package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 服务端命中箱表：数值一律对齐 OceanClient 3.3.8 的 {@code mappedDimsOrNull}
 * （1.8.9 Hypixel 实测），并锁住「表外回退客户端 AABB」「缩放」「最小边」三条规则。
 */
class AimTargetDimsTest {

    @Test
    void tableMatchesOceanClientNumbers() {
        assertDims("zombie", 0.9, 2.0, 0.9);
        assertDims("zombie_baby", 1.2, 0.8, 1.2);
        assertDims("zombified_piglin", 0.9, 2.0, 0.9);
        assertDims("skeleton", 0.9, 2.0, 0.9);
        assertDims("wither_skeleton", 1.0, 2.6, 1.0);
        assertDims("blaze", 0.9, 2.0, 0.9);
        assertDims("wolf", 1.5, 0.7, 1.5);
        assertDims("wolf_baby", 0.5, 0.3, 0.5);
        assertDims("silverfish", 0.2, 0.4, 0.2);
        assertDims("endermite", 0.2, 0.4, 0.2);
        assertDims("witch", 0.9, 1.75, 0.9);
        assertDims("creeper", 0.9, 1.1, 0.9);
        assertDims("cave_spider", 0.5, 0.7, 0.5);
        assertDims("giant", 3.9, 12.0, 3.9);
        assertDims("ghast", 4.4, 4.4, 4.4);
        assertDims("iron_golem", 1.8, 2.7, 1.8);
    }

    @Test
    void criticalBoxesAreWiderThanVanillaClientAabb() {
        // 这张表的价值就在「比客户端 AABB 宽」——僵尸 0.9 vs 原版 0.6、狼 1.5 vs 0.6。
        // 若有人把它改成原版数值，这条会红。
        assertEquals(true, AimTargetDims.forKey("zombie").width() > 0.6);
        assertEquals(true, AimTargetDims.forKey("wolf").width() > 0.6);
        assertEquals(true, AimTargetDims.forKey("iron_golem").width() > 1.4);
    }

    @Test
    void slimeEdgeScalesWithSize() {
        assertEquals(0.51, AimTargetDims.forKey("slime:1").height(), 1.0e-9);
        assertDims("slime:3", 1.53, 1.53, 1.53);
        // size 非法或缺失时回退客户端 AABB，而不是猜一个数
        assertNull(AimTargetDims.forKey("slime:"));
        assertNull(AimTargetDims.forKey("slime:big"));
    }

    @Test
    void unknownKeyFallsBackToClientAabb() {
        AimTargetDims.Dims client = new AimTargetDims.Dims(0.6, 1.95, 0.6);
        assertEquals(client, AimTargetDims.resolve("spider", client, 1.0));
        assertEquals(client, AimTargetDims.resolve(null, client, 1.0));
        assertNull(AimTargetDims.resolve("spider", null, 1.0));
    }

    @Test
    void scaleMultipliesEveryEdgeAndKeepsAMinimum() {
        AimTargetDims.Dims scaled = AimTargetDims.resolve("zombie", null, 1.5);
        assertEquals(1.35, scaled.width(), 1.0e-9);
        assertEquals(3.0, scaled.height(), 1.0e-9);
        assertEquals(1.35, scaled.depth(), 1.0e-9);

        // 极端缩放不能把盒子压成一条线（否则描点会因为浮点误差判不出命中）
        AimTargetDims.Dims tiny = AimTargetDims.resolve("silverfish", null, 0.2);
        assertEquals(AimTargetDims.MIN_EDGE, tiny.width(), 1.0e-9);    // 0.2×0.2 = 0.04 → 0.05
        assertEquals(0.08, tiny.height(), 1.0e-9);                     // 0.4×0.2 仍在门槛之上
        assertEquals(AimTargetDims.MIN_EDGE, tiny.depth(), 1.0e-9);
    }

    @Test
    void scaleOfOneKeepsTableValuesUntouched() {
        assertNotNull(AimTargetDims.resolve("giant", null, 1.0));
        assertDims("giant", 3.9, 12.0, 3.9);
        AimTargetDims.Dims zero = AimTargetDims.resolve("zombie", null, 0.0);
        assertEquals(0.9, zero.width(), 1.0e-9);
    }

    private static void assertDims(String key, double width, double height, double depth) {
        AimTargetDims.Dims dims = AimTargetDims.forKey(key);
        assertNotNull(dims, "表里缺 " + key);
        assertEquals(width, dims.width(), 1.0e-9, key + " 宽");
        assertEquals(height, dims.height(), 1.0e-9, key + " 高");
        assertEquals(depth, dims.depth(), 1.0e-9, key + " 深");
    }
}

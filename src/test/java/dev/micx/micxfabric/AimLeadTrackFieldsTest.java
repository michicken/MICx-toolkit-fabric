package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2 防回归（tasks.md T2-TR1 / spec AC-5）：Track 必须携带 Forge 全套
 * 渲染状态字段（兜底速度/平滑位置/滞回/碰撞与地面缓存/L0 影子），
 * 缺任何一个都会直接退化体感（无平滑=抖、无兜底=尖峰瞬断、无滞回=闪烁）。
 */
class AimLeadTrackFieldsTest {

    @Test
    void trackCarriesFullForgeRenderState() {
        Set<String> fields = Arrays.stream(AimLeadModule.Track.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());
        Set<String> required = new HashSet<>(Arrays.asList(
                "ts", "xs", "ys", "zs",               // 环形样本
                "lastSeenMs",
                "lsx", "lsy", "lsz",                  // L0 影子（服务器最后坐标）
                "rx", "ry", "rz",                     // 帧间平滑位置
                "showing",                            // 显示滞回
                "lvx", "lvy", "lvz", "lvAt",          // 上个可信速度兜底
                "clx", "clz", "clampAtMs",            // 碰撞钳制缓存（120ms）
                "clAx", "clAz",                       // 钳制缓存失效锚点（脚底换列）
                "turnDegPerSec", "tauScale",          // 转向检测（打转治乱）
                "leadErrMs", "leadErrBlocks", "leadErrCount", "leadErrIdx",   // 提前量误差诊断
                "predX", "predZ", "predY", "predAt", "predCount", "lastPredRecordMs",   // 预测回检队列
                "grX", "grY0", "grZ", "grGround", "grAtMs"));   // 落点面高缓存（300ms）
        for (String name : required) {
            assertTrue(fields.contains(name), "Track missing field: " + name);
        }
    }

    @Test
    void ringBufferIndexResolvesNewestFirst() {
        AimLeadModule.Track track = new AimLeadModule.Track();
        for (int i = 0; i < 15; i++) track.add(i, i, i, i);   // 溢出环形
        org.junit.jupiter.api.Assertions.assertEquals(12, track.size());
        org.junit.jupiter.api.Assertions.assertEquals(14.0, track.xs[track.idx(0)], 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(13.0, track.xs[track.idx(1)], 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(3.0, track.xs[track.idx(11)], 1e-9);
    }
}

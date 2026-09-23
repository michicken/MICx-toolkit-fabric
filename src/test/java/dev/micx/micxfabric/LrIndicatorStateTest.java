package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 无敌怪判定的 LR 门控信号源（用户定稿 2026-09-16）。 */
class LrIndicatorStateTest {
    @Test
    void releasedWithinSeesOnlyRealReleases() {
        LrIndicatorState s = new LrIndicatorState();
        long now = 1_000_000L;
        assertFalse(s.releasedWithin(now, 15_000L), "还没放过 LR");
        assertTrue(s.tryRelease(now));
        assertTrue(s.releasedWithin(now, 15_000L));
        assertTrue(s.releasedWithin(now + 14_999L, 15_000L));
        assertFalse(s.releasedWithin(now + 15_001L, 15_000L), "15 秒窗口过期");
        // 新的一次释放重新打开窗口
        assertTrue(s.tryRelease(now + 30_000L));
        assertTrue(s.releasedWithin(now + 30_000L, 15_000L));
        assertTrue(s.releasedWithin(now + 44_000L, 15_000L));
        assertFalse(s.releasedWithin(now + 46_000L, 15_000L));
    }

    @Test
    void dedupedReleasesDoNotReopenTheWindow() {
        LrIndicatorState s = new LrIndicatorState();
        long now = 2_000_000L;
        assertTrue(s.tryRelease(now));
        // 180ms 内的第二个雷声包被去重（一次 LR 服务端连发两个包，用户定稿 2026-09-23）
        assertFalse(s.tryRelease(now + 100L));
        assertTrue(s.releasedWithin(now + 100L, 15_000L));
        // 窗口仍按第一次释放算：到 15 秒就关
        assertFalse(s.releasedWithin(now + 15_001L, 15_000L));
    }

    @Test
    void distantReleasesBothCountAfterDedupWindow() {
        LrIndicatorState s = new LrIndicatorState();
        long now = 3_000_000L;
        s.setMaxPlayers(4);
        assertTrue(s.tryRelease(now));
        assertTrue(s.tryRelease(now + 200L), "间隔超过 180ms 的第二发 LR 正常入队");
        assertEquals(2, s.greenCount(now + 200L));
    }

    @Test
    void teamSyncCorrectionOnlyRaisesTheQueue() {
        LrIndicatorState s = new LrIndicatorState();
        long now = 4_000_000L;
        s.setMaxPlayers(4);
        // 本地只听到 1 发，TeamSync 说 3 个队友放过 → 补齐到 3（用户定稿 2026-09-23）
        assertTrue(s.tryRelease(now));
        s.correctUpTo(now + 500L, 3);
        assertEquals(3, s.greenCount(now + 500L));
        // 队友数更少时不动本地计数（只增不减）
        s.correctUpTo(now + 600L, 2);
        assertEquals(3, s.greenCount(now + 600L));
        // 超过人数上限的坏数据（9 > 4 人）整条忽略，不补不清
        s.correctUpTo(now + 700L, 9);
        assertEquals(3, s.greenCount(now + 700L));
    }
}

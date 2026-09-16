package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

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
        // 500ms 内的连点被去重（不记录）
        assertFalse(s.tryRelease(now + 100L));
        assertTrue(s.releasedWithin(now + 100L, 15_000L));
        // 窗口仍按第一次释放算：到 15 秒就关
        assertFalse(s.releasedWithin(now + 15_001L, 15_000L));
    }
}

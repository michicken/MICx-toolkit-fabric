package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RankUpTool 的离线回归：文本拼装、档位归一化、3 秒节奏与到点判定。 */
class RankUpToolRulesTest {

    @Test
    void messageIsRankPlusPls() {
        assertEquals("VIP pls", RankUpToolRules.message("VIP"));
        assertEquals("MVP+ pls", RankUpToolRules.message("MVP+"));
        assertEquals("MVP++ pls", RankUpToolRules.message("MVP++"));
    }

    @Test
    void rankNormalizesCaseAndWhitespace() {
        assertEquals("MVP++", RankUpToolRules.normalizeRank(" mvp++ "));
        assertEquals("VIP+", RankUpToolRules.normalizeRank("vip+"));
        assertEquals(RankUpToolRules.DEFAULT_RANK, RankUpToolRules.normalizeRank(null));
        assertEquals(RankUpToolRules.DEFAULT_RANK, RankUpToolRules.normalizeRank(""));
        assertEquals(RankUpToolRules.DEFAULT_RANK, RankUpToolRules.normalizeRank("ADMIN"));
    }

    @Test
    void allFiveHypixelRanksAreOfferedInOrder() {
        assertEquals(java.util.List.of("VIP", "VIP+", "MVP", "MVP+", "MVP++"), RankUpToolRules.RANKS);
    }

    @Test
    void intervalDefaultsToThreeSecondsAndClamps() {
        assertEquals(3, RankUpToolRules.DEFAULT_INTERVAL_SECONDS);
        assertEquals(3_000L, RankUpToolRules.intervalMillis(3));
        // 0 / 负数视为「没填」→ 回落默认 3 秒，而不是变成每 tick 一条
        assertEquals(3, RankUpToolRules.clampIntervalSeconds(0));
        assertEquals(3, RankUpToolRules.clampIntervalSeconds(-5));
        assertEquals(RankUpToolRules.MIN_INTERVAL_SECONDS, RankUpToolRules.clampIntervalSeconds(1));
        assertEquals(RankUpToolRules.MAX_INTERVAL_SECONDS,
                RankUpToolRules.clampIntervalSeconds(RankUpToolRules.MAX_INTERVAL_SECONDS + 1));
    }

    @Test
    void dueFiresImmediatelyThenEveryInterval() {
        long interval = RankUpToolRules.intervalMillis(3);
        // 本次激活还没发过 → 立刻发第一条
        assertTrue(RankUpToolRules.due(1_000L, -1L, interval));
        // 刚发完不到 3 秒 → 不发
        assertFalse(RankUpToolRules.due(1_000L + 2_999L, 1_000L, interval));
        // 满 3 秒 → 发
        assertTrue(RankUpToolRules.due(1_000L + 3_000L, 1_000L, interval));
        assertTrue(RankUpToolRules.due(1_000L + 9_500L, 1_000L, interval));
    }

    @Test
    void dueNeverSpamsWhenIntervalIsDegenerate() {
        // 间隔被填成 0 / 负数时，硬下限兜底成 1 秒，绝不会退化成每 tick 一条
        assertFalse(RankUpToolRules.due(1_000L, 1_000L, 0L));
        assertFalse(RankUpToolRules.due(1_000L + 999L, 1_000L, -1L));
        assertTrue(RankUpToolRules.due(1_000L + 1_000L, 1_000L, 0L));
    }

    @Test
    void systemClockGoingBackwardsDoesNotStallForever() {
        // 往回改时钟后不该卡死到「永远差一个间隔」
        assertTrue(RankUpToolRules.due(500L, 10_000L, 3_000L));
        // 但也不该在正常时间轴上误判为到点
        assertFalse(RankUpToolRules.due(9_999L, 10_000L - 2_000L, 3_000L));
    }

    @Test
    void remainingCountsDownToNextSend() {
        long interval = RankUpToolRules.intervalMillis(3);
        assertEquals(0L, RankUpToolRules.remainingMillis(1_000L, -1L, interval));
        assertEquals(1_000L, RankUpToolRules.remainingMillis(3_000L, 1_000L, interval));
        assertEquals(0L, RankUpToolRules.remainingMillis(5_000L, 1_000L, interval));
        assertEquals(0L, RankUpToolRules.remainingMillis(500L, 10_000L, interval));
    }
}

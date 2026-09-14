package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 书本界面警报的离线回归：0.5 秒节奏、关书即停、1 分钟上限、卡顿不补发。 */
class RankUpToolBookAlertTest {

    @Test
    void openDingsImmediatelyThenEveryHalfSecond() {
        RankUpToolBookAlert alert = new RankUpToolBookAlert();
        long t = 10_000L;
        assertTrue(alert.tick(true, t));
        assertTrue(alert.ringing());
        assertFalse(alert.tick(true, t + 499L));
        assertTrue(alert.tick(true, t + 500L));
        assertFalse(alert.tick(true, t + 999L));
        assertTrue(alert.tick(true, t + 1_000L));
    }

    @Test
    void closingBookStopsImmediatelyAndReopenRestarts() {
        RankUpToolBookAlert alert = new RankUpToolBookAlert();
        assertTrue(alert.tick(true, 1_000L));
        assertFalse(alert.tick(false, 1_200L));
        assertFalse(alert.ringing());
        assertFalse(alert.tick(false, 60_000L));
        // 关掉再打开：从头开始，重新立刻第一叮
        assertTrue(alert.tick(true, 61_000L));
        assertTrue(alert.ringing());
    }

    @Test
    void alertStopsAfterOneMinuteEvenWhileBookStaysOpen() {
        RankUpToolBookAlert alert = new RankUpToolBookAlert();
        long start = 5_000L;
        assertTrue(alert.tick(true, start));
        int dings = 1;
        // 模拟 20 tps：每 50ms 一 tick，数一分钟内总共响了几声
        for (long t = start + 50L; t <= start + RankUpToolBookAlert.MAX_DURATION_MS + 500L; t += 50L) {
            if (alert.tick(true, t)) dings++;
        }
        // 第 0 秒 + 第 0.5…59.5 秒 = 120 声；第 60 秒整已出窗口
        assertEquals(120, dings);
        assertFalse(alert.ringing());
        // 书一直开着也不再响
        assertFalse(alert.tick(true, start + 120_000L));
        // 关掉重开才重新计时
        assertFalse(alert.tick(false, start + 121_000L));
        assertTrue(alert.tick(true, start + 122_000L));
    }

    @Test
    void lagSpikeDoesNotBurstCatchUpDings() {
        RankUpToolBookAlert alert = new RankUpToolBookAlert();
        assertTrue(alert.tick(true, 0L));
        // 卡了 3 秒：只补一叮，不把欠的 6 声一次放出来
        assertTrue(alert.tick(true, 3_000L));
        assertFalse(alert.tick(true, 3_001L));
        assertFalse(alert.tick(true, 3_100L));
        assertTrue(alert.tick(true, 3_500L));
    }

    @Test
    void closedBookNeverDings() {
        RankUpToolBookAlert alert = new RankUpToolBookAlert();
        for (long t = 0L; t < 10_000L; t += 250L) {
            assertFalse(alert.tick(false, t));
        }
        assertFalse(alert.ringing());
        assertEquals(0L, alert.remainingMillis(5_000L));
    }

    @Test
    void remainingCountsDownWithinTheWindow() {
        RankUpToolBookAlert alert = new RankUpToolBookAlert();
        assertTrue(alert.tick(true, 1_000L));
        assertEquals(RankUpToolBookAlert.MAX_DURATION_MS, alert.remainingMillis(1_000L));
        assertEquals(30_000L, alert.remainingMillis(31_000L));
        assertEquals(0L, alert.remainingMillis(61_000L));
        alert.reset();
        assertEquals(0L, alert.remainingMillis(1_000L));
    }
}

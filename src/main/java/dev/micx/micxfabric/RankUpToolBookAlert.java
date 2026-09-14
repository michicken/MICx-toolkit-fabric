package dev.micx.micxfabric;

/**
 * RankUpTool「书本界面警报」的纯逻辑：打开书本界面后每 0.5 秒「叮」一声，最长 60 秒；
 * 书本界面一关立即停，重新打开重新计时。
 *
 * <p>用户定稿 2026-09-15：触发范围=成书阅读 / 书与笔编辑 / 签名页三种界面；停止条件=关掉书本界面
 * （上限 1 分钟）；节奏=每 0.5 秒一响；音量按常规即可，不做超大声。这里不碰任何客户端状态，
 * 方便离线回归——铃声时序在游戏里很难复现（要手头真有一本书，还得掐表）。
 */
public final class RankUpToolBookAlert {
    /** 两次「叮」的间隔。 */
    public static final long DING_INTERVAL_MS = 500L;
    /** 单次打开书本界面最多响多久。 */
    public static final long MAX_DURATION_MS = 60_000L;

    private boolean ringing;
    /** 本次打开的 1 分钟窗口已用完；书本还开着也不再响，直到关掉重开。 */
    private boolean exhausted;
    private long startMs = -1L;
    private long nextDingMs = -1L;

    /**
     * 每个客户端 tick 调一次。
     *
     * @param bookOpen 当前是不是书本界面
     * @param nowMs    当前时间
     * @return 这一次 tick 是否该叮一声（每 tick 最多一声）
     */
    public boolean tick(boolean bookOpen, long nowMs) {
        if (!bookOpen) {
            reset();
            return false;
        }
        if (exhausted) return false;
        if (!ringing) {
            // 刚打开书本界面：立刻第一叮，并起 1 分钟窗口。
            ringing = true;
            startMs = nowMs;
            nextDingMs = nowMs + DING_INTERVAL_MS;
            return true;
        }
        if (nowMs - startMs >= MAX_DURATION_MS) {
            exhausted = true;
            return false;
        }
        if (nowMs >= nextDingMs) {
            // 卡顿后不补发欠的一串：下一声从当前时间重新落点。
            nextDingMs = nowMs + DING_INTERVAL_MS;
            return true;
        }
        return false;
    }

    /** 当前是否在响铃窗口内。 */
    public boolean ringing() {
        return ringing && !exhausted;
    }

    /** 本次窗口还剩多少毫秒；没在响返回 0。 */
    public long remainingMillis(long nowMs) {
        if (!ringing() || startMs < 0L || nowMs < startMs) return 0L;
        return Math.max(0L, MAX_DURATION_MS - (nowMs - startMs));
    }

    /** 清空状态（关书、换世界、关闭模块）。 */
    public void reset() {
        ringing = false;
        exhausted = false;
        startMs = -1L;
        nextDingMs = -1L;
    }
}

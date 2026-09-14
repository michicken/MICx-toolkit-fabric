package dev.micx.micxfabric;

import java.util.List;

/**
 * RankUpTool 的纯逻辑：档位归一化、间隔钳位、文本拼装、到点判定。
 *
 * <p>用户定稿 2026-09-15：每 3 秒发一条、任何场合都发、文本固定为「&lt;选中档位&gt; pls」。
 * 这里不碰任何客户端状态，方便离线回归——发送节奏改坏了在游戏里很难发现（要么没发、
 * 要么已经因为刷屏被 mute 了）。
 */
public final class RankUpToolRules {
    /** Hypixel 可赠送的五个档位，按升序排列；面板按同一顺序展示。 */
    public static final List<String> RANKS = List.of("VIP", "VIP+", "MVP", "MVP+", "MVP++");
    public static final String DEFAULT_RANK = "VIP";
    /** 文本固定后缀（用户定稿：不做模板框）。 */
    public static final String MESSAGE_SUFFIX = " pls";
    public static final int DEFAULT_INTERVAL_SECONDS = 3;
    public static final int MIN_INTERVAL_SECONDS = 1;
    public static final int MAX_INTERVAL_SECONDS = 600;

    private RankUpToolRules() {
    }

    /** 归一化档位：忽略大小写与首尾空格；不认识的输入回落到默认档位。 */
    public static String normalizeRank(String rank) {
        if (rank == null) return DEFAULT_RANK;
        String trimmed = rank.trim();
        for (String candidate : RANKS) {
            if (candidate.equalsIgnoreCase(trimmed)) return candidate;
        }
        return DEFAULT_RANK;
    }

    /** 面板填的秒数 → 合法间隔；非正数回落到默认 3 秒。 */
    public static int clampIntervalSeconds(int seconds) {
        if (seconds <= 0) return DEFAULT_INTERVAL_SECONDS;
        return Math.min(MAX_INTERVAL_SECONDS, Math.max(MIN_INTERVAL_SECONDS, seconds));
    }

    /** 间隔毫秒（始终是钳位后的值，下限 1 秒）。 */
    public static long intervalMillis(int seconds) {
        return clampIntervalSeconds(seconds) * 1000L;
    }

    /** 实际发出去的文本：「&lt;档位&gt; pls」。 */
    public static String message(String rank) {
        return normalizeRank(rank) + MESSAGE_SUFFIX;
    }

    /**
     * 是否到点该发。
     *
     * @param nowMs      当前时间
     * @param lastSentMs 上次发送时间；{@code < 0} 表示本次激活还没发过 → 立即发第一条
     * @return 到点返回 true
     */
    public static boolean due(long nowMs, long lastSentMs, long intervalMs) {
        if (lastSentMs < 0L) return true;
        // 系统时间被往回改时不要卡死：直接放行一条，随后用新的 now 重新落点。
        if (nowMs < lastSentMs) return true;
        return nowMs - lastSentMs >= Math.max(MIN_INTERVAL_SECONDS * 1000L, intervalMs);
    }

    /** 距下一条还有多少毫秒（面板倒计时用；已到点返回 0）。 */
    public static long remainingMillis(long nowMs, long lastSentMs, long intervalMs) {
        if (lastSentMs < 0L || nowMs < lastSentMs) return 0L;
        long interval = Math.max(MIN_INTERVAL_SECONDS * 1000L, intervalMs);
        return Math.max(0L, interval - (nowMs - lastSentMs));
    }
}

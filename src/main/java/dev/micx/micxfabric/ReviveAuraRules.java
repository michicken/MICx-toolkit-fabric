package dev.micx.micxfabric;

/**
 * 救援光环的纯逻辑：按目标分开的冷却（0.2.110 用户定稿，推翻 0.2.103 的全局串行轮换）。
 *
 * <p>冷却属于每个目标自己：给 A 发包只有 A 进入 intervalMs 冷却，
 * 没发过的 B <b>不受 A 影响、可立刻发</b>；每 tick 仍最多发一包（保守节奏）。
 * 间隔语义 = 「对同一目标的最小重发间隔」，只在倒地标记被清后又立刻重新判倒地的抖动时挡连点。
 */
public final class ReviveAuraRules {
    /** 两次救援发包的最小间隔默认值（毫秒）——沿用 Forge 面板口径。 */
    public static final double DEFAULT_INTERVAL_MS = 200.0;
    public static final double MIN_INTERVAL_MS = 50.0;
    public static final double MAX_INTERVAL_MS = 1000.0;

    private ReviveAuraRules() {
    }

    public static double clampInterval(double value) {
        return Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, value));
    }

    /** 距上次发包是否已够最小间隔（lastMs = 0 表示还没发过）。 */
    public static boolean intervalReady(long nowMs, long lastMs, double intervalMs) {
        return (double) (nowMs - lastMs) >= intervalMs;
    }

    /**
     * 从候选里挑本 tick 要救的那一只（每 tick 最多一包）。
     *
     * <p>{@code lastSentMs[i]} 是<b>该目标自己</b>上次被发包的毫秒时间戳，{@code <= 0} 表示从没发过。
     * 可发 = 从没发过，或已过对它的 intervalMs 冷却；可发者里取最近。
     * 关键语义：A 在冷却不拖累 B——B 只要没被点过就永远立刻可选。
     * 全都不可发或没有候选返回 -1。
     */
    public static int nextSendIndex(double[] distances, long[] lastSentMs, long nowMs, double intervalMs) {
        if (distances == null || lastSentMs == null || distances.length == 0
                || distances.length != lastSentMs.length) {
            return -1;
        }
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < distances.length; i++) {
            long last = lastSentMs[i];
            if (last > 0L && !intervalReady(nowMs, last, intervalMs)) continue;
            if (best < 0 || distances[i] < bestDist) {
                best = i;
                bestDist = distances[i];
            }
        }
        return best;
    }
}

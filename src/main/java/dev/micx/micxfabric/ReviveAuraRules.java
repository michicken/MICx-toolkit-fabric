package dev.micx.micxfabric;

/**
 * 救援光环的纯逻辑：发包节流 + 多目标轮换。
 *
 * <p>用户定稿 2026-09-16：场上同时有两个倒地（Sleeping）队友且在范围内时，
 * <b>先点 1 号，间隔到了就点 2 号，而不是又点 1 号</b>。
 * 所以选靶规则是「优先不是上一次点过的那只，同级取最近」，只剩它一只时才重复点。
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
     * 从候选里选下一个要救的目标下标。
     *
     * <p>第一优先：不是 {@code lastTargetId}（上一次点过的那只）里最近的一只；
     * 候选里只剩上一次那只时才返回它；没有候选返回 -1。
     */
    public static int chooseIndex(int[] ids, double[] distances, int lastTargetId) {
        if (ids == null || distances == null || ids.length == 0
                || ids.length != distances.length) {
            return -1;
        }
        int bestAny = -1;
        double bestAnyDist = Double.MAX_VALUE;
        int bestOther = -1;
        double bestOtherDist = Double.MAX_VALUE;
        for (int i = 0; i < ids.length; i++) {
            double distance = distances[i];
            if (bestAny < 0 || distance < bestAnyDist) {
                bestAny = i;
                bestAnyDist = distance;
            }
            if (ids[i] != lastTargetId && (bestOther < 0 || distance < bestOtherDist)) {
                bestOther = i;
                bestOtherDist = distance;
            }
        }
        return bestOther >= 0 ? bestOther : bestAny;
    }
}

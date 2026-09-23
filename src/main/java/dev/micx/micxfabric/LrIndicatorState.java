package dev.micx.micxfabric;

import java.util.ArrayDeque;

final class LrIndicatorState {
    static final long LR_ACTIVE_MS = 18_000L;
    /**
     * 雷声去重窗（用户定稿 2026-09-23）：一次 LR 服务端连发两个雷声包（SST 每包必记所以
     * 1 LR 记 2），180ms 足够合并同一次的双包；两发真实 LR 间隔通常远大于此，不会被吞。
     */
    static final long DEDUP_MS = 180L;
    static final float LR_PITCH_MIN = 0.5f;
    static final float LR_PITCH_MAX = 1.3f;
    static final long GOLEM_WINDOW_MS = 2_000L;
    static final float GOLEM_DIST = 6.0f;
    static final int[] BOSS_ROUNDS = {25, 35, 56, 57, 101};
    static final long HIDE_AFTER_ALL_RED_MS = 4_000L;

    static boolean isLrPitch(float pitch) {
        return pitch > LR_PITCH_MIN && pitch < LR_PITCH_MAX;
    }

    static boolean isBossRound(int round) {
        for (int r : BOSS_ROUNDS) if (r == round) return true;
        return false;
    }

    private final ArrayDeque<Long> releases = new ArrayDeque<>();
    private final ArrayDeque<float[]> golemJoins = new ArrayDeque<>();
    private int maxPlayers;
    private volatile boolean bossFirstConsumed;
    private int rotationCounter;
    private long lastGreenAtMs = -1;

    void setMaxPlayers(int n) { maxPlayers = Math.max(0, n); }
    int maxPlayers() { return maxPlayers; }

    int onLrReleased(long now) { tryRelease(now); return greenCount(now); }

    boolean tryRelease(long now) {
        if (!releases.isEmpty() && now - releases.peekLast() <= DEDUP_MS) return false;
        releases.addLast(now);
        rotationCounter++;
        return true;
    }

    /**
     * TeamSync 纠偏（用户定稿 2026-09-23）：本地雷声计数把贴近的 LR 合并掉/漏听了时，
     * 用队友 lr_release 的人数把队列补齐到不小于该值——只增不减、不响蜂鸣轮换照常推进。
     */
    void correctUpTo(long now, int teammateReleases) {
        prune(now);
        int cap = maxPlayers > 0 ? maxPlayers : 4;
        if (teammateReleases <= releases.size() || teammateReleases > cap) return;
        while (releases.size() < teammateReleases) {
            releases.addLast(now);
            rotationCounter++;
        }
    }

    void onRoundChanged(int round) { bossFirstConsumed = false; }

    boolean isBossFirstThunder(int round) {
        if (!isBossRound(round) || bossFirstConsumed) return false;
        bossFirstConsumed = true;
        return true;
    }

    int rotationCount() { return rotationCounter; }

    boolean shouldHide(long now, int green) {
        if (green > 0) { lastGreenAtMs = now; return false; }
        if (lastGreenAtMs < 0) lastGreenAtMs = now;
        return now - lastGreenAtMs > HIDE_AFTER_ALL_RED_MS;
    }

    void onGolemJoin(long now, double x, double y, double z) {
        golemJoins.addLast(new float[]{(float) now, (float) x, (float) y, (float) z});
        while (!golemJoins.isEmpty() && now - (long) golemJoins.peekFirst()[0] > GOLEM_WINDOW_MS) golemJoins.removeFirst();
    }

    boolean isGolemThunder(long now, float sx, float sy, float sz) {
        for (float[] g : golemJoins) {
            if (now - (long) g[0] > GOLEM_WINDOW_MS) continue;
            double d2 = (sx - g[1]) * (sx - g[1]) + (sy - g[2]) * (sy - g[2]) + (sz - g[3]) * (sz - g[3]);
            if (d2 <= GOLEM_DIST * GOLEM_DIST) return true;
        }
        return false;
    }

    int greenCount(long now) {
        prune(now);
        int n = releases.size();
        return maxPlayers > 0 ? Math.min(n, maxPlayers) : 0;
    }

    /**
     * 最近 {@code windowMs} 内是否释放过 LR（供无敌怪判定门控联动，用户定稿 2026-09-16）。
     *
     * <p>只看真实记录进 {@link #releases} 的释放（去重/爆发抑制掉的不算）。
     */
    boolean releasedWithin(long now, long windowMs) {
        prune(now);
        return !releases.isEmpty() && now - releases.peekFirst() <= windowMs;
    }

    void reset() {
        releases.clear(); golemJoins.clear();
        maxPlayers = 0; bossFirstConsumed = false; rotationCounter = 0; lastGreenAtMs = -1;
    }

    private void prune(long now) {
        while (!releases.isEmpty() && now - releases.peekFirst() > LR_ACTIVE_MS) releases.removeFirst();
    }
}

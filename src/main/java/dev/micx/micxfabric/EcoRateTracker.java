package dev.micx.micxfabric;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-2-minute pure gold growth tracker.
 * Self via chat +N Gold events, mates via scoreboard positive deltas.
 * Platform-agnostic, no Minecraft dependency.
 */
public final class EcoRateTracker {
    public static final long WINDOW_MS = 120_000L;
    public static final long REFRESH_MS = 10_000L;
    private static final long MATE_STALE_MS = WINDOW_MS * 2;

    private final ArrayDeque<long[]> selfGains = new ArrayDeque<>();
    private final ConcurrentHashMap<String, ArrayDeque<long[]>> samples = new ConcurrentHashMap<>();

    private volatile long firstSelfGainMs;
    private volatile long selfRate = -1L;
    private volatile Map<String, Long> rates = Collections.emptyMap();
    private long lastRefreshMs;

    public void onSelfGoldGain(long now, int amount) {
        if (amount <= 0) return;
        synchronized (selfGains) {
            if (firstSelfGainMs == 0L) firstSelfGainMs = now;
            selfGains.addLast(new long[]{now, amount});
            pruneGains(now);
        }
    }

    public void samplePlayer(String name, int gold, long now) {
        if (name == null || gold < 0) return;
        ArrayDeque<long[]> q = samples.get(name);
        if (q == null) {
            q = new ArrayDeque<>();
            ArrayDeque<long[]> prev = samples.putIfAbsent(name, q);
            if (prev != null) q = prev;
        }
        synchronized (q) {
            long[] last = q.peekLast();
            if (last != null && last[1] == gold) return;
            q.addLast(new long[]{now, gold});
            pruneSamples(q, now);
        }
    }

    public void tick(long now) {
        if (now - lastRefreshMs < REFRESH_MS) return;
        lastRefreshMs = now;

        long newSelf;
        synchronized (selfGains) {
            pruneGains(now);
            if (firstSelfGainMs == 0L) {
                newSelf = -1L;
            } else {
                long sum = 0;
                for (long[] g : selfGains) sum += g[1];
                newSelf = sum;
            }
        }

        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, ArrayDeque<long[]>> en : samples.entrySet()) {
            ArrayDeque<long[]> q = en.getValue();
            synchronized (q) {
                pruneSamples(q, now);
                if (q.isEmpty() || now - q.peekLast()[0] > MATE_STALE_MS) continue;
                long sum = 0;
                long prev = Long.MIN_VALUE;
                for (long[] s : q) {
                    if (prev != Long.MIN_VALUE && s[1] > prev) sum += s[1] - prev;
                    prev = s[1];
                }
                out.put(en.getKey(), sum);
            }
        }
        selfRate = newSelf;
        rates = out;
    }

    public long selfRate() {
        return selfRate;
    }

    public long rate(String name) {
        Long v = rates.get(name);
        return v == null ? -1L : v;
    }

    public void reset() {
        synchronized (selfGains) {
            selfGains.clear();
        }
        firstSelfGainMs = 0L;
        samples.clear();
        selfRate = -1L;
        rates = Collections.emptyMap();
        lastRefreshMs = 0L;
    }

    private void pruneGains(long now) {
        long cutoff = now - WINDOW_MS;
        while (!selfGains.isEmpty() && selfGains.peekFirst()[0] < cutoff) selfGains.pollFirst();
    }

    private static void pruneSamples(ArrayDeque<long[]> q, long now) {
        long cutoff = now - WINDOW_MS;
        while (q.size() > 1) {
            java.util.Iterator<long[]> it = q.iterator();
            it.next();
            long[] second = it.next();
            if (second[0] < cutoff) q.pollFirst();
            else break;
        }
    }
}

package dev.micx.micxfabric;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

/** Estimates whether currently spawned enemies can be cleared before the next wave. */
public final class WaveTempoEstimator {
    private static final long WINDOW_MS = 6_000L;
    private static final long MIN_SAMPLE_MS = 1_500L;
    private static final int MIN_KILLS = 2;

    private static final class Sample {
        private final long at;
        private final int zombiesLeft;

        private Sample(long at, int zombiesLeft) {
            this.at = at;
            this.zombiesLeft = zombiesLeft;
        }
    }

    public static final class Snapshot {
        public final boolean valid;
        public final int round;
        public final int nextWave;
        public final int activeEnemies;
        public final double killsPerSecond;
        public final long nextWaveMs;
        public final long clearEtaMs;
        public final long overlapMs;

        private Snapshot(boolean valid, int round, int nextWave, int activeEnemies,
                         double killsPerSecond, long nextWaveMs, long clearEtaMs,
                         long overlapMs) {
            this.valid = valid;
            this.round = round;
            this.nextWave = nextWave;
            this.activeEnemies = activeEnemies;
            this.killsPerSecond = killsPerSecond;
            this.nextWaveMs = nextWaveMs;
            this.clearEtaMs = clearEtaMs;
            this.overlapMs = overlapMs;
        }

        private static Snapshot unavailable(int round, int nextWave, int activeEnemies,
                                            long nextWaveMs) {
            return new Snapshot(false, round, nextWave, activeEnemies, 0.0,
                    nextWaveMs, -1L, 0L);
        }
    }

    private final Deque<Sample> samples = new ArrayDeque<>();
    private int round = -1;
    private Snapshot snapshot = Snapshot.unavailable(0, 0, 0, -1L);

    public void reset() {
        samples.clear();
        round = -1;
        snapshot = Snapshot.unavailable(0, 0, 0, -1L);
    }

    public Snapshot update(long now, int currentRound, int nextWave, int zombiesLeft,
                           int activeEnemies, long nextWaveMs) {
        if (currentRound != round) {
            samples.clear();
            round = currentRound;
        }
        while (!samples.isEmpty() && now - samples.peekFirst().at > WINDOW_MS) {
            samples.removeFirst();
        }
        if (zombiesLeft >= 0 && (samples.isEmpty() || now > samples.peekLast().at)) {
            samples.addLast(new Sample(now, zombiesLeft));
        }

        if (nextWave <= 0 || nextWaveMs < 0L || activeEnemies < 0) {
            snapshot = Snapshot.unavailable(currentRound, nextWave, activeEnemies, nextWaveMs);
            return snapshot;
        }
        if (activeEnemies == 0) {
            snapshot = new Snapshot(true, currentRound, nextWave, 0, 0.0,
                    nextWaveMs, 0L, -nextWaveMs);
            return snapshot;
        }
        if (samples.size() < 2) {
            snapshot = Snapshot.unavailable(currentRound, nextWave, activeEnemies, nextWaveMs);
            return snapshot;
        }

        Sample first = samples.peekFirst();
        Sample previous = null;
        int kills = 0;
        for (Sample sample : samples) {
            if (previous != null && sample.zombiesLeft < previous.zombiesLeft) {
                kills += previous.zombiesLeft - sample.zombiesLeft;
            }
            previous = sample;
        }
        long duration = previous == null ? 0L : previous.at - first.at;
        if (duration < MIN_SAMPLE_MS || kills < MIN_KILLS) {
            snapshot = Snapshot.unavailable(currentRound, nextWave, activeEnemies, nextWaveMs);
            return snapshot;
        }

        double rate = kills * 1_000.0 / duration;
        long eta = (long) Math.ceil(activeEnemies * 1_000.0 / rate);
        snapshot = new Snapshot(true, currentRound, nextWave, activeEnemies, rate,
                nextWaveMs, eta, eta - nextWaveMs);
        return snapshot;
    }

    public Snapshot snapshot() {
        return snapshot;
    }
}

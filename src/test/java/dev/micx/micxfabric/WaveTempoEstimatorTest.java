package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveTempoEstimatorTest {
    @Test
    void estimatesClearEtaAndOverlapFromRecentKills() {
        WaveTempoEstimator estimator = new WaveTempoEstimator();
        estimator.update(0L, 50, 3, 20, 18, 7_100L);
        estimator.update(1_000L, 50, 3, 18, 16, 6_100L);
        estimator.update(2_000L, 50, 3, 16, 14, 5_100L);
        WaveTempoEstimator.Snapshot result =
                estimator.update(3_000L, 50, 3, 14, 12, 4_100L);

        assertTrue(result.valid);
        assertEquals(2.0, result.killsPerSecond, 0.001);
        assertEquals(6_000L, result.clearEtaMs);
        assertEquals(1_900L, result.overlapMs);
        assertEquals("W3 4.1s | CLEAR ETA 6.0s | OVERLAP +1.9s",
                ZombiesAssistModule.waveTempoLine(result));
        assertEquals("W3 4.0s | CLEAR ETA 5.9s | OVERLAP +1.9s",
                ZombiesAssistModule.waveTempoLine(result, 125L));
    }

    @Test
    void resetsSamplesOnRoundChangeAndHandlesEmptyWorld() {
        WaveTempoEstimator estimator = new WaveTempoEstimator();
        estimator.update(0L, 49, 2, 20, 12, 3_000L);
        estimator.update(2_000L, 49, 2, 16, 8, 1_000L);
        assertTrue(estimator.update(2_500L, 49, 3, 15, 10, 9_500L).valid);

        WaveTempoEstimator.Snapshot empty =
                estimator.update(3_000L, 50, 1, 88, 0, 10_000L);
        assertTrue(empty.valid);
        assertEquals(0L, empty.clearEtaMs);
        assertEquals(-10_000L, empty.overlapMs);

        WaveTempoEstimator.Snapshot learning =
                estimator.update(3_500L, 50, 1, 88, 4, 9_500L);
        assertFalse(learning.valid);
    }

    @Test
    void keepsForgeWaveScheduleSeparateFromTextNormalization() {
        assertEquals(34, ZombiesRoundData.waveTimes(39)[2]);
        assertEquals(30, ZombiesRoundData.waveTimes(47)[2]);
        assertEquals(27, ZombiesRoundData.waveTimes(74)[4]);
        assertEquals(5, ZombiesRoundData.waveTimes(101)[0]);
        assertEquals(5, ZombiesRoundData.waveTimes(105)[0]);
        assertEquals(4, ZombiesRoundData.waveAt(74, 26_999L));
        assertEquals(5, ZombiesRoundData.waveAt(74, 27_000L));
    }
}

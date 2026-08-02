package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombiesRoundDataTest {
    @Test
    void exposesForgeSpecialWaveTablesForAllLightningRounds() {
        assertArrayEquals(new int[]{2, 3}, ZombiesRoundData.tooWaves(70));
        assertArrayEquals(new int[]{2, 3}, ZombiesRoundData.tooWaves(80));
        assertArrayEquals(new int[]{2, 3}, ZombiesRoundData.tooWaves(90));
        assertArrayEquals(new int[]{2, 3}, ZombiesRoundData.tooWaves(100));
        assertArrayEquals(new int[]{4, 5, 6}, ZombiesRoundData.tooGiantWaves(100));
        assertArrayEquals(new int[]{4, 5, 6}, ZombiesRoundData.giantWaves(95));
        assertTrue(ZombiesRoundData.isLsRound(70));
        assertTrue(ZombiesRoundData.isLsRound(80));
        assertTrue(ZombiesRoundData.isLsRound(90));
        assertTrue(ZombiesRoundData.isLsRound(100));
    }

    @Test
    void staticMobClearCountdownHasExactFiveMinuteBoundary() {
        int[] times = ZombiesRoundData.waveTimes(70);
        long lastWave = times[times.length - 1] * 1_000L;
        assertEquals(300_000L, ZombiesRoundData.mobClearRemainingMs(70, lastWave));
        assertEquals(299_999L, ZombiesRoundData.mobClearRemainingMs(70, lastWave + 1L));
        assertEquals(-1L, ZombiesRoundData.mobClearRemainingMs(70, lastWave + 300_000L));
        assertEquals(-1L, ZombiesRoundData.mobClearRemainingMs(0, 0L));
    }

    @Test
    void weakForecastsUseForgeProbabilityThresholds() {
        assertEquals("DG advantage R24,R28,R30,R37,R39,R40", ZombiesRoundData.dgForecast(24));
        assertEquals("CARP advantage R5,R39,R41,R43,R46,R48", ZombiesRoundData.carpForecast(1));
        assertEquals("BG advantage R9,R10,R11,R12", ZombiesRoundData.bgForecast(1));
        assertNull(ZombiesRoundData.carpForecast(55));
        assertEquals(0, ZombiesRoundData.weakPuProb("unknown", 1));
    }
}

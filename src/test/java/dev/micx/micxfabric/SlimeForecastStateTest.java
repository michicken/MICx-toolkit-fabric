package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlimeForecastStateTest {
    @Test
    void hiddenOutsideTableRounds() {
        SlimeForecastState state = new SlimeForecastState();
        state.setRound(1, 1);
        assertEquals(SlimeForecastState.Phase.HIDDEN, state.phase());
        state.setRound(44, 3);
        assertEquals(SlimeForecastState.Phase.HIDDEN, state.phase());
    }

    @Test
    void brightDuringCurrentUnspawnedWaveDarkBetweenWaves() {
        SlimeForecastState state = new SlimeForecastState();
        // R10 每波都刷 {1..6}：进入 w2、w1 已刷 → 预告波 = 2（当前波）→ 亮绿
        state.setRound(10, 2);
        state.onSlimeSpawned(1);
        assertEquals(SlimeForecastState.Phase.BRIGHT, state.phase());
        // w2 刷完、还没进 w3（wave=3 但 w3 未刷出标记仍为 2）→ 预告波 = 3 > 当前波 → 墨绿
        state.onSlimeSpawned(2);
        assertEquals(SlimeForecastState.Phase.DARK, state.phase());
        state.setRound(10, 3);
        state.onSlimeSpawned(3);
        assertEquals(SlimeForecastState.Phase.DARK, state.phase());     // w3 已刷 → 预告转 w4 墨绿
    }

    @Test
    void hiddenAfterLastWaveSpawned() {
        SlimeForecastState state = new SlimeForecastState();
        state.setRound(9, 6);      // R9 只有 w6 一波
        state.onSlimeSpawned(6);
        assertEquals(SlimeForecastState.Phase.HIDDEN, state.phase());
    }

    @Test
    void roundChangeResetsSpawnedMarker() {
        SlimeForecastState state = new SlimeForecastState();
        state.setRound(15, 6);
        state.onSlimeSpawned(6);
        assertEquals(SlimeForecastState.Phase.HIDDEN, state.phase());
        state.setRound(16, 1);     // 新回合：lastSpawnedWave 归零
        assertEquals(SlimeForecastState.Phase.BRIGHT, state.phase());   // w1 是当前波
    }

    @Test
    void staleSpawnMarkerDoesNotBlockFutureWaves() {
        SlimeForecastState state = new SlimeForecastState();
        state.setRound(47, 4);     // R47 波次 {1,2,3}
        state.onSlimeSpawned(3);
        assertEquals(SlimeForecastState.Phase.HIDDEN, state.phase());
    }
}

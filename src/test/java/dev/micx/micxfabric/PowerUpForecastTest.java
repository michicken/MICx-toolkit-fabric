package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerUpForecastTest {
    @Test
    void maxAmmoUsesOnlyLockedCompactCombinations() {
        assertEquals(List.of("x1 x6 Max Ammo"),
                PowerUpForecast.lines(10, 2, null).stream().map(PowerUpForecast.Line::text).toList());
        assertEquals(List.of("x2 x7 Max Ammo"),
                PowerUpForecast.lines(10, 3, null).stream().map(PowerUpForecast.Line::text).toList());
        assertTrue(PowerUpForecast.lines(10, 0, null).isEmpty());
    }

    @Test
    void instaKillStopsAtRoundTwentyFive() {
        assertEquals(List.of("INS R23"),
                PowerUpForecast.lines(24, 0, "R23").stream().map(PowerUpForecast.Line::text).toList());
        assertTrue(PowerUpForecast.lines(25, 2, "R26").stream()
                .noneMatch(line -> "insta".equals(line.kind())));
    }
}

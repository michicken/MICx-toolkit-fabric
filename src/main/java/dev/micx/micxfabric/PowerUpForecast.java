package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.List;

/** Compact Power-up forecast shared by the HUD and Minecraft-free tests. */
final class PowerUpForecast {
    private PowerUpForecast() {
    }

    static List<Line> lines(int round, int maxGroup, String instaForecast) {
        List<Line> result = new ArrayList<>(2);
        if (round > 0 && round < 25 && instaForecast != null) {
            result.add(new Line("INS " + instaForecast, 0xFFFF5964, "insta"));
        }
        String max = maxGroup == 2 ? "x1 x6 Max Ammo" : maxGroup == 3 ? "x2 x7 Max Ammo" : null;
        if (max != null) result.add(new Line(max, 0xFF4D8CFF, "max"));
        return List.copyOf(result);
    }

    record Line(String text, int color, String kind) {
    }
}

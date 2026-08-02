package dev.micx.micxfabric;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure Forge-compatible sound classifier and recent fire metrics reducer. */
public final class ZombiesSoundMetrics {
    public static final long FIRE_WINDOW_MS = 600L;
    private static final double MAX_PLAYER_DISTANCE_SQ = 9.0;

    private final Map<String, FireSample> lastFire = new HashMap<>();
    private int lrUses;
    private int hitsNormal;
    private int hitsCrit;

    public void reset() {
        lastFire.clear();
        lrUses = 0;
        hitsNormal = 0;
        hitsCrit = 0;
    }

    public void observe(String soundId, float pitch, double soundX, double soundY, double soundZ,
                        long now, boolean alienArcadium, List<PlayerPoint> players) {
        if (!alienArcadium || soundId == null || soundId.isBlank()) return;
        String id = normalize(soundId);
        if (isLightning(id) && !near(pitch, 2.0f)) lrUses++;
        if (isSuccessfulHit(id)) {
            if (near(pitch, 1.5f)) hitsNormal++;
            else if (near(pitch, 2.0f)) hitsCrit++;
        }

        String weapon = weaponOf(id, pitch);
        if (weapon == null || players == null) return;
        PlayerPoint nearest = nearest(soundX, soundY, soundZ, players);
        if (nearest != null) lastFire.put(nearest.name(), new FireSample(now, weapon));
    }

    public int lrUses() {
        return lrUses;
    }

    public int hits() {
        return hitsNormal + hitsCrit;
    }

    public int crits() {
        return hitsCrit;
    }

    public boolean isFiring(String name, long now) {
        FireSample sample = name == null ? null : lastFire.get(name);
        return sample != null && now - sample.at() >= 0L && now - sample.at() <= FIRE_WINDOW_MS;
    }

    public String fireWeapon(String name, long now) {
        if (!isFiring(name, now)) return null;
        FireSample sample = lastFire.get(name);
        return sample == null ? null : sample.weapon();
    }

    static String weaponOf(String soundId, float pitch) {
        String id = normalize(soundId);
        if (matches(id, "mob.irongolem.hit", "entity.iron_golem.attack") && near(pitch, 2.5f)) {
            return "Pistol";
        }
        if (matches(id, "random.explode", "entity.generic.explode") && near(pitch, 2.5f)) {
            return "Shotgun";
        }
        if (matches(id, "fireworks.blast_far", "entity.firework_rocket.blast_far") && near(pitch, 0.5f)) {
            return "Sniper";
        }
        if (matches(id, "fireworks.largeblast", "entity.firework_rocket.large_blast")) {
            if (near(pitch, 0.8f)) return "DBarrel";
            if (near(pitch, 2.5f)) return "Rifle";
            return null;
        }
        if (matches(id, "fire.ignite", "block.fire.ambient") && near(pitch, 0.5f)) {
            return "Zapper";
        }
        if (matches(id, "dig.stone", "block.stone.hit") && near(pitch, 2.0f)) {
            return "GDigger";
        }
        if (matches(id, "fire.fire", "block.fire.ambient") && near(pitch, 2.0f)) {
            return "Flamer";
        }
        if (isLightning(id) && near(pitch, 2.0f)) return "Elder";
        return null;
    }

    private static boolean isLightning(String id) {
        return matches(id, "ambient.weather.thunder", "entity.lightning_bolt.thunder");
    }

    private static boolean isSuccessfulHit(String id) {
        return matches(id, "random.successful_hit", "entity.player.attack.strong");
    }

    private static PlayerPoint nearest(double x, double y, double z, List<PlayerPoint> players) {
        PlayerPoint best = null;
        double bestDistance = MAX_PLAYER_DISTANCE_SQ;
        for (PlayerPoint player : players) {
            if (player == null || player.name() == null || player.name().isBlank()) continue;
            double dx = player.x() - x;
            double dy = player.y() - y;
            double dz = player.z() - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static boolean matches(String id, String... values) {
        for (String value : values) if (value.equals(id)) return true;
        return false;
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 0.06f;
    }

    private static String normalize(String soundId) {
        String id = soundId == null ? "" : soundId.toLowerCase(Locale.ROOT).trim();
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        return id;
    }

    public record PlayerPoint(String name, double x, double y, double z) {
    }

    private record FireSample(long at, String weapon) {
    }
}

package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Forge-compatible tracker for slime deaths caused by sustained natural suffocation damage. */
public final class SlimeEcoTracker {
    static final double GOLD_PER_HP = 2.0;
    static final int STUCK_STEPS = 4;
    static final float MIN_GROWN_MHP = 45.0f;
    private static final float NATURAL_DAMAGE_MIN = 0.1f;
    private static final float PLAYER_DAMAGE_MIN = 3.0f;
    private static final double MIN_DISTANCE = 6.0;

    static final class Sample {
        final int id;
        final float health;
        final float maxHealth;
        final double distance;

        Sample(int id, float health, float maxHealth, double distance) {
            this.id = id;
            this.health = health;
            this.maxHealth = maxHealth;
            this.distance = distance;
        }
    }

    private static final class Record {
        float peakMaxHealth;
        float lastHealth = -1.0f;
        int stuckSteps;
        double lastDistance;
        boolean lastHitByPlayer;
    }

    private final Map<Integer, Record> live = new HashMap<>();
    private int stuckDeaths;
    private double lostGold;

    public int stuckDeaths() {
        return stuckDeaths;
    }

    public int lostGold() {
        return (int) lostGold;
    }

    public boolean hasData() {
        return stuckDeaths > 0;
    }

    public void reset() {
        live.clear();
        stuckDeaths = 0;
        lostGold = 0.0;
    }

    /**
     * Applies one entity snapshot. Missing ids are resolved as deaths or unloads using the
     * same blood-level and sustained-damage rules as Forge.
     */
    void update(Collection<Sample> samples) {
        Set<Integer> seen = new HashSet<>();
        if (samples != null) {
            for (Sample sample : samples) {
                if (sample == null || sample.maxHealth <= 0.0f) continue;
                seen.add(sample.id);
                Record record = live.computeIfAbsent(sample.id, ignored -> new Record());
                if (record.lastHealth >= 0.0f) {
                    float damage = record.lastHealth - sample.health;
                    if (damage > PLAYER_DAMAGE_MIN) {
                        record.stuckSteps = 0;
                        record.lastHitByPlayer = true;
                    } else if (damage > NATURAL_DAMAGE_MIN) {
                        record.stuckSteps++;
                        record.lastHitByPlayer = false;
                    } else {
                        record.stuckSteps = 0;
                        record.lastHitByPlayer = false;
                    }
                }
                record.lastHealth = sample.health;
                record.peakMaxHealth = Math.max(record.peakMaxHealth, sample.maxHealth);
                record.lastDistance = sample.distance;
            }
        }

        Iterator<Map.Entry<Integer, Record>> iterator = live.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Record> entry = iterator.next();
            if (seen.contains(entry.getKey())) continue;
            Record record = entry.getValue();
            iterator.remove();
            if (record.lastHealth > record.peakMaxHealth * 0.4f) continue;
            boolean stuck = record.stuckSteps >= STUCK_STEPS
                    && !record.lastHitByPlayer
                    && record.lastDistance > MIN_DISTANCE
                    && record.peakMaxHealth >= MIN_GROWN_MHP;
            if (!stuck) continue;
            stuckDeaths++;
            lostGold += record.peakMaxHealth * GOLD_PER_HP;
        }
    }

    /** Samples live slimes from the current client world; dead entities disappear on the next sample. */
    public void tick(Minecraft client, long now) {
        if (client == null || client.level == null || client.player == null) return;
        Collection<Sample> samples = new ArrayList<>();
        try {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof AbstractCubeMob cube) || !cube.isAlive()
                        || cube.isRemoved() || cube.isDeadOrDying()) continue;
                samples.add(new Sample(cube.getId(), cube.getHealth(), cube.getMaxHealth(),
                        cube.distanceTo(client.player)));
            }
        } catch (RuntimeException ignored) {
            return;
        }
        update(samples);
    }
}

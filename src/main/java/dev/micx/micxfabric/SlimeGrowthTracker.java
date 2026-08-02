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

/** Tracks Forge-compatible slime growth stages from client-side entity samples. */
public final class SlimeGrowthTracker {
    static final int ATTACK_REMAINING_STAGES = 2;
    private static final int SLIME_GROWTHS = 4;
    private static final int MAGMA_GROWTHS = 8;
    private static final int GROWTH_TICKS = 60;
    private static final long TICK_MS = 50L;

    static final class Sample {
        final int id;
        final boolean magma;
        final float maxHealth;
        final int ticksExisted;

        Sample(int id, boolean magma, float maxHealth, int ticksExisted) {
            this.id = id;
            this.magma = magma;
            this.maxHealth = maxHealth;
            this.ticksExisted = Math.max(0, ticksExisted);
        }
    }

    private static final class Rec {
        final int id;
        boolean magma;
        int stage;
        float lastMaxHealth;
        long lastGrowthAt;
        int ticksExisted;

        Rec(Sample sample, long now) {
            id = sample.id;
            magma = sample.magma;
            stage = initialStage(sample);
            lastMaxHealth = sample.maxHealth;
            ticksExisted = sample.ticksExisted;
            lastGrowthAt = approximateLastGrowthAt(sample.ticksExisted, now);
        }
    }

    public static final class Snapshot {
        public final boolean present;
        public final int count;
        public final String smallestKind;
        public final int smallestStage;
        public final int totalStages;
        public final int remainingStages;
        public final long nextGrowthMs;
        public final boolean attackReady;
        public final boolean allMax;

        private Snapshot(boolean present, int count, String smallestKind, int smallestStage,
                         int totalStages, int remainingStages, long nextGrowthMs,
                         boolean attackReady, boolean allMax) {
            this.present = present;
            this.count = count;
            this.smallestKind = smallestKind;
            this.smallestStage = smallestStage;
            this.totalStages = totalStages;
            this.remainingStages = remainingStages;
            this.nextGrowthMs = nextGrowthMs;
            this.attackReady = attackReady;
            this.allMax = allMax;
        }

        static Snapshot empty() {
            return new Snapshot(false, 0, null, 0, 0, 0, 0L, false, false);
        }
    }

    private final Map<Integer, Rec> live = new HashMap<>();
    private Snapshot snapshot = Snapshot.empty();
    private Object activeLevel;

    public void reset() {
        live.clear();
        snapshot = Snapshot.empty();
        activeLevel = null;
    }

    public void tick(Minecraft client, long now) {
        if (client == null || client.level == null) {
            reset();
            return;
        }
        if (activeLevel != client.level) {
            live.clear();
            snapshot = Snapshot.empty();
            activeLevel = client.level;
        }
        Collection<Sample> samples = new ArrayList<>();
        try {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof AbstractCubeMob cube) || !cube.isAlive()
                        || cube.isRemoved() || cube.isDeadOrDying()) continue;
                samples.add(new Sample(cube.getId(), cube instanceof MagmaCube,
                        cube.getMaxHealth(), cube.tickCount));
            }
        } catch (RuntimeException ignored) {
            return;
        }
        update(samples, now);
    }

    void update(Collection<Sample> samples, long now) {
        Set<Integer> seen = new HashSet<>();
        if (samples != null) {
            for (Sample sample : samples) {
                if (sample == null) continue;
                seen.add(sample.id);
                Rec rec = live.get(sample.id);
                if (rec == null) {
                    live.put(sample.id, new Rec(sample, now));
                    continue;
                }
                int before = rec.stage;
                int maxGrowths = maxGrowths(sample.magma);
                int ageStage = Math.min(maxGrowths, sample.ticksExisted / GROWTH_TICKS);
                int healthStage = inferStageFromHealth(sample.magma, sample.maxHealth);
                int inferred = Math.max(ageStage, healthStage);
                boolean maxChanged = Math.abs(sample.maxHealth - rec.lastMaxHealth) > 0.5f;
                if (maxChanged && inferred <= rec.stage && now - rec.lastGrowthAt >= 500L) {
                    inferred = Math.min(maxGrowths, rec.stage + 1);
                }
                rec.stage = Math.max(rec.stage, Math.min(maxGrowths, inferred));
                if (rec.stage > before) rec.lastGrowthAt = now;
                rec.magma = sample.magma;
                rec.lastMaxHealth = sample.maxHealth;
                rec.ticksExisted = sample.ticksExisted;
            }
        }

        Iterator<Map.Entry<Integer, Rec>> iterator = live.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!seen.contains(iterator.next().getKey())) iterator.remove();
        }
        rebuildSnapshot(now);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private void rebuildSnapshot(long now) {
        if (live.isEmpty()) {
            snapshot = Snapshot.empty();
            return;
        }
        Rec smallest = null;
        int worstRemaining = -1;
        boolean allMax = true;
        for (Rec rec : live.values()) {
            int remaining = maxGrowths(rec.magma) - rec.stage;
            if (remaining > 0) allMax = false;
            if (smallest == null || remaining > worstRemaining
                    || (remaining == worstRemaining && rec.stage < smallest.stage)) {
                smallest = rec;
                worstRemaining = remaining;
            }
        }
        int maxGrowths = maxGrowths(smallest.magma);
        long nextMs = worstRemaining <= 0 ? 0L : nextGrowthMs(smallest, now);
        snapshot = new Snapshot(true, live.size(), smallest.magma ? "BLOB" : "SLIME",
                smallest.stage + 1, maxGrowths + 1, worstRemaining, nextMs,
                worstRemaining <= ATTACK_REMAINING_STAGES, allMax);
    }

    private static int initialStage(Sample sample) {
        return Math.max(Math.min(maxGrowths(sample.magma), sample.ticksExisted / GROWTH_TICKS),
                inferStageFromHealth(sample.magma, sample.maxHealth));
    }

    private static int inferStageFromHealth(boolean magma, float maxHealth) {
        float base = magma ? 50f : 25f;
        int maxGrowths = maxGrowths(magma);
        int stage = Math.round((maxHealth - base) / 20f);
        stage = Math.max(0, Math.min(maxGrowths, stage));
        float expected = base + stage * 20f;
        return Math.abs(expected - maxHealth) <= 5f ? stage : 0;
    }

    private static int maxGrowths(boolean magma) {
        return magma ? MAGMA_GROWTHS : SLIME_GROWTHS;
    }

    private static long approximateLastGrowthAt(int ticksExisted, long now) {
        int within = ticksExisted % GROWTH_TICKS;
        return now - within * TICK_MS;
    }

    private static long nextGrowthMs(Rec rec, long now) {
        long byObservedGrowth = rec.lastGrowthAt + GROWTH_TICKS * TICK_MS - now;
        if (byObservedGrowth > 0L && byObservedGrowth <= GROWTH_TICKS * TICK_MS) {
            return byObservedGrowth;
        }
        int within = rec.ticksExisted % GROWTH_TICKS;
        return (GROWTH_TICKS - within) * TICK_MS;
    }
}

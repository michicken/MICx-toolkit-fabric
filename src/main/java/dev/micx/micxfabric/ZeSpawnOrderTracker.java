package dev.micx.micxfabric;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Port of ZE SpawnPatternNotice.java — per-round spawn order state. Minecraft-free where possible. */
public final class ZeSpawnOrderTracker {
    private boolean startCollecting = true;
    public final List<LivingEntity> powerupEnsured = new ArrayList<>();
    public final List<LivingEntity> powerupPredict = new ArrayList<>();
    final List<LivingEntity> badhsMobList = new ArrayList<>();
    final List<LivingEntity> allEntities = new ArrayList<>();
    public final List<Entity> entitiesOnLine = new ArrayList<>();
    final ZombiesPuRounds puRounds = new ZombiesPuRounds();

    void resetRound() {
        powerupEnsured.clear();
        powerupPredict.clear();
        badhsMobList.clear();
        allEntities.clear();
        entitiesOnLine.clear();
        startCollecting = true;
    }

    void resetSession() {
        resetRound();
        puRounds.reset();
    }

    boolean isZombiesCandidate(LivingEntity e) {
        if (e == null) return false;
        if (e instanceof Zombie) return true;
        if (e instanceof Slime) return true;
        if (e instanceof Wolf) return true;
        if (e instanceof Witch) return true;
        if (e instanceof Endermite) return true;
        if (e instanceof Creeper) return true;
        if (e instanceof Blaze) return true;
        if (e instanceof Skeleton) return true;
        if (e instanceof Ghast) return true;
        if (e instanceof IronGolem) return true;
        if (e instanceof Squid) return true;
        if (e instanceof Silverfish) return true;
        if (e instanceof Giant) return true;
        if (e instanceof CaveSpider) return true;
        if (e instanceof MushroomCow) return true;
        if (e instanceof Ocelot) return true;
        if (e instanceof Guardian g && g.getMaxHealth() > 30) return true;
        if (e instanceof Chicken c && !c.isInvisible()) return true;
        if (e instanceof MagmaCube) return true; // 1.8.9 EntityMagmaCube was in ZE MobSpawnOrder but not in candidate list; 26.2 treat likewise for consistency
        return false;
    }

    private static boolean isExcludedFromPowerup(LivingEntity e, WaveTable.ZbMap map) {
        if (e instanceof Squid) return true;
        if (e instanceof Chicken) return true;
        if (e instanceof MushroomCow) return true;
        if (e instanceof Wolf && map == null) {
            // Alien Arcadium alien dog check deferred to caller with distance
            return false;
        }
        return false;
    }

    void onEntityJoin(LivingEntity e, int currentRound, WaveTable.ZbMap map,
                        boolean powerupDetector, int predictorCount, boolean badHeadShot) {
        if (!isZombiesCandidate(e)) return;
        if (powerupDetector) {
            boolean alienDog = false;
            if (e instanceof Wolf && e.distanceToSqr(-16.5, 72, -0.5) <= 36) alienDog = true;
            if (!isExcludedFromPowerup(e, map) && !alienDog) {
                boolean flag = puRounds.insRounds().isEmpty() && puRounds.maxRounds().isEmpty() && puRounds.ssRounds().isEmpty();
                int predict = flag ? (currentRound == 1 ? 1 : 2) : predictorCount;
                int amount = puRounds.amountFor(currentRound);
                if (startCollecting) {
                    if (powerupEnsured.size() == amount && powerupPredict.size() < predict) {
                        if (!powerupPredict.contains(e) && !powerupEnsured.contains(e)) powerupPredict.add(e);
                    }
                    if (powerupEnsured.size() < amount) {
                        if (!powerupEnsured.contains(e) && !powerupPredict.contains(e)) powerupEnsured.add(e);
                    }
                    if (powerupEnsured.size() == amount && powerupPredict.size() == predict) startCollecting = false;
                }
            }
        }
        if (badHeadShot) {
            if (!badhsMobList.contains(e)) badhsMobList.add(e);
        }
        allEntities.add(e);
    }

    void tickCleanup() {
        List<LivingEntity> all = new ArrayList<>(allEntities);
        all.addAll(badhsMobList);
        for (LivingEntity e : all) {
            if (e == null || e.isRemoved() || e.isDeadOrDying() || e.getHealth() <= 0) {
                allEntities.remove(e);
                badhsMobList.remove(e);
            }
        }
        powerupEnsured.removeIf(e -> e == null || e.isRemoved() || e.isDeadOrDying() || e.getHealth() <= 0);
        powerupPredict.removeIf(e -> e == null || e.isRemoved() || e.isDeadOrDying() || e.getHealth() <= 0);
        entitiesOnLine.removeIf(en -> {
            if (!(en instanceof LivingEntity le)) return true;
            return le.isRemoved() || le.isDeadOrDying() || le.getHealth() <= 0;
        });
    }

    void onEntitiesRemoved(int[] ids) {
        if (ids == null || ids.length == 0) return;
        java.util.Set<Integer> gone = new java.util.HashSet<>();
        for (int id : ids) gone.add(id);
        allEntities.removeIf(e -> e != null && gone.contains(e.getId()));
        badhsMobList.removeIf(e -> e != null && gone.contains(e.getId()));
        powerupEnsured.removeIf(e -> e != null && gone.contains(e.getId()));
        powerupPredict.removeIf(e -> e != null && gone.contains(e.getId()));
        entitiesOnLine.removeIf(en -> en != null && gone.contains(en.getId()));
    }

    List<LivingEntity> ensuredSnapshot() { return Collections.unmodifiableList(new ArrayList<>(powerupEnsured)); }
    List<LivingEntity> predictSnapshot() { return Collections.unmodifiableList(new ArrayList<>(powerupPredict)); }
    List<LivingEntity> badhsSnapshot() { return Collections.unmodifiableList(new ArrayList<>(badhsMobList)); }
    List<Entity> onLineSnapshot() { return Collections.unmodifiableList(new ArrayList<>(entitiesOnLine)); }

    public LivingEntity badHeadshotAnchor() {
        if (badhsMobList.isEmpty()) return null;
        return badhsMobList.get(badhsMobList.size() - 1);
    }
}

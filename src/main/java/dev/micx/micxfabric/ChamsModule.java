package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** Textured model Chams; deliberately separate from the box ESP module. */
public final class ChamsModule implements Module {
    private static final ChamsModule INSTANCE = new ChamsModule();
    static final int DEFAULT_RANGE = 48;
    static final int MIN_RANGE = 8;
    static final int MAX_RANGE = 128;

    private volatile boolean enabled;
    private volatile int range = DEFAULT_RANGE;
    private volatile boolean configLoaded;

    private ChamsModule() {
    }

    public static ChamsModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "chams";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (!enabled) resetState();
        ModuleStateStore.put(id(), enabled);
    }

    public int range() {
        loadConfig();
        return range;
    }

    public void setRange(int value) {
        range = clampRange(value);
        saveConfig();
    }

    static int clampRange(int value) {
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, value));
    }

    public boolean shouldApply(LivingEntity entity, Minecraft client, boolean activeRender) {
        if (entity == null || client == null || client.level == null || client.player == null) {
            return false;
        }
        boolean target = isTarget(entity);
        double distanceSq = client.player.distanceToSqr(entity);
        return ChamsRenderDecision.decide(enabled, client.level != null, client.player != null,
                target, distanceSq, range(), false, activeRender)
                == ChamsRenderDecision.Result.APPLY;
    }

    static boolean isTarget(LivingEntity entity) {
        return ChamsTargetRules.isTarget(
                entity instanceof WitherBoss,
                entity instanceof Player,
                entity instanceof Villager,
                entity instanceof Enemy,
                entity instanceof Wolf,
                entity instanceof IronGolem,
                !entity.isRemoved() && !entity.isDeadOrDying(),
                entity.isDeadOrDying());
    }

    @Override
    public void resetState() {
    }

    private synchronized void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("chams.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ModelChams.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        range = ConfigProperties.integer(properties, "range", DEFAULT_RANGE, MIN_RANGE, MAX_RANGE);
    }

    void saveConfig() {
        loadConfig();
        Properties properties = new Properties();
        properties.setProperty("range", Integer.toString(clampRange(range)));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("chams.properties"), properties,
                    "MICx textured model Chams configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save Chams configuration", exception);
        }
    }
}

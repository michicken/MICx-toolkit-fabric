package dev.micx.micxfabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Client-side Power-up drop tracker migrated from the Forge ArmorStand scan.
 * The tracker owns only observed drops; activation timers remain in the event reducer.
 */
public final class ZombiesPowerUpTracker {
    private static final ZombiesPowerUpTracker INSTANCE = new ZombiesPowerUpTracker();
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int BEAM_BOTTOM_ALPHA = 0x52;
    private static final int BEAM_TOP_ALPHA = 0x0D;
    private static final double BEAM_HALF_WIDTH = 0.22;
    private static final double BEAM_HEIGHT = 40.0;

    private final PowerUpTimer timer = new PowerUpTimer();
    private final Map<Integer, DropVisual> visibleDrops = new HashMap<>();
    private Object activeLevel;
    private int tickCounter;

    private ZombiesPowerUpTracker() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    public static ZombiesPowerUpTracker instance() {
        return INSTANCE;
    }

    public void tick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            reset();
            return;
        }
        if (activeLevel != client.level) {
            reset();
            activeLevel = client.level;
        }
        if (!ZombiesTracker.instance().isInAlienArcadium()) {
            resetDropsOnly();
            return;
        }
        long now = System.currentTimeMillis();
        timer.expire(now);
        if ((tickCounter++ % SCAN_INTERVAL_TICKS) != 0) {
            removeExpiredVisuals();
            return;
        }
        scan(client, now);
    }

    public void reset() {
        timer.clear();
        visibleDrops.clear();
        PowerUpGroupState.instance().clear();
        activeLevel = null;
        tickCounter = 0;
    }

    public Map<Integer, DropVisual> dropsSnapshot() {
        return Map.copyOf(visibleDrops);
    }

    public Map<Integer, PowerUpTimer.Drop> timerDropsSnapshot() {
        return timer.dropsSnapshot();
    }

    public long dropRemainingMs(int entityId, long now) {
        return timer.dropRemainingMs(entityId, now);
    }

    public int maxGroup() {
        return groupFor("max");
    }

    public int instaGroup() {
        return groupFor("insta");
    }

    public int shoppingGroup() {
        return groupFor("shopping");
    }

    public String maxForecast(int round) {
        return PowerUpGroupState.instance().forecast("max", round);
    }

    public String instaForecast(int round) {
        return PowerUpGroupState.instance().forecast("insta", round);
    }

    public String shoppingForecast(int round) {
        return PowerUpGroupState.instance().forecast("shopping", round);
    }

    private void scan(Minecraft client, long now) {
        Set<Integer> currentIds = new HashSet<>();
        int round = ZombiesTracker.instance().round();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)
                    || stand.isDeadOrDying()
                    || !stand.hasCustomName()) {
                continue;
            }
            String kind = kindFromName(stand.getCustomName().getString());
            if (kind == null) continue;
            int id = stand.getId();
            currentIds.add(id);
            timer.observeDrop(kind, id, round, now);
            PowerUpTimer.Drop drop = timer.dropsSnapshot().get(id);
            if (drop == null) continue;
            lockGroupByDrop(kind, drop.round());
            visibleDrops.put(id, new DropVisual(
                    id, kind, drop.round(), stand.getX(), stand.getY(), stand.getZ(),
                    drop.firstSeenAt(), drop.expiresAt()));
        }
        visibleDrops.keySet().removeIf(id -> !currentIds.contains(id));
        removeExpiredVisuals();
    }

    private void removeExpiredVisuals() {
        Map<Integer, PowerUpTimer.Drop> drops = timer.dropsSnapshot();
        visibleDrops.keySet().removeIf(id -> !drops.containsKey(id));
    }

    private void resetDropsOnly() {
        timer.clear();
        visibleDrops.clear();
        PowerUpGroupState.instance().clear();
        tickCounter = 0;
    }

    private void lockGroupByDrop(String kind, int round) {
        // Group selection is deliberately based on the observed drop round, never activation.
        // Later valid drops may replace an earlier provisional group, matching Forge's cadence lock.
        if (round <= 0) return;
        PowerUpGroupState state = PowerUpGroupState.instance();
        state.lock(kind, round);
    }

    private int groupFor(String kind) {
        return PowerUpGroupState.instance().group(kind);
    }

    private void collectSubmits(LevelRenderContext context) {
        ZombiesAssistModule module = ZombiesAssistModule.instance();
        if (!module.enabled() || !module.config().puBeam
                || !ZombiesTracker.instance().isInAlienArcadium()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;
        Map<Integer, DropVisual> snapshot = dropsSnapshot();
        if (snapshot.isEmpty()) return;

        SubmitNodeCollector collector = context.submitNodeCollector();
        PoseStack pose = context.poseStack();
        Vec3 camera = client.gameRenderer.mainCamera().position();
        for (DropVisual drop : snapshot.values()) {
            if (drop.expiresAt() <= System.currentTimeMillis()) continue;
            submitBeam(collector, pose, drop, camera);
        }
    }

    private static void submitBeam(SubmitNodeCollector collector, PoseStack pose,
                                   DropVisual drop, Vec3 camera) {
        double x = drop.x() - camera.x;
        double y = drop.y() - camera.y;
        double z = drop.z() - camera.z;
        int color = color(drop.kind());
        collector.submitCustomGeometry(pose, PowerUpRenderTypes.powerUpBeam(),
                (stackPose, consumer) -> drawBeam(consumer, stackPose, x, y, z, color));
    }

    private static void drawBeam(VertexConsumer consumer, PoseStack.Pose pose,
                                 double x, double y, double z, int color) {
        double w = BEAM_HALF_WIDTH;
        double top = y + BEAM_HEIGHT;
        int bottomColor = (color & 0x00FFFFFF) | (BEAM_BOTTOM_ALPHA << 24);
        int topColor = (color & 0x00FFFFFF) | (BEAM_TOP_ALPHA << 24);
        quad(consumer, pose,
                x - w, y, z - w,
                x - w, top, z - w,
                x + w, top, z - w,
                x + w, y, z - w,
                bottomColor, topColor);
        quad(consumer, pose,
                x + w, y, z - w,
                x + w, top, z - w,
                x + w, top, z + w,
                x + w, y, z + w,
                bottomColor, topColor);
        quad(consumer, pose,
                x + w, y, z + w,
                x + w, top, z + w,
                x - w, top, z + w,
                x - w, y, z + w,
                bottomColor, topColor);
        quad(consumer, pose,
                x - w, y, z + w,
                x - w, top, z + w,
                x - w, top, z - w,
                x - w, y, z - w,
                bottomColor, topColor);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz,
                             int bottomColor, int topColor) {
        vertex(consumer, pose, ax, ay, az, bottomColor);
        vertex(consumer, pose, bx, by, bz, topColor);
        vertex(consumer, pose, cx, cy, cz, topColor);
        vertex(consumer, pose, dx, dy, dz, bottomColor);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               double x, double y, double z, int color) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24);
    }

    private static String kindFromName(String value) {
        if (value == null) return null;
        StringBuilder stripped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\u00a7' && i + 1 < value.length()) {
                i++;
                continue;
            }
            stripped.append(character);
        }
        String name = stripped.toString().toUpperCase(Locale.ROOT);
        if (name.contains("MAX AMMO")) return "max";
        if (name.contains("INSTA KILL")) return "insta";
        if (name.contains("SHOPPING SPREE")) return "shopping";
        if (name.contains("DOUBLE GOLD")) return "dg";
        if (name.contains("BONUS GOLD")) return "bg";
        return null;
    }

    private static int color(String kind) {
        return switch (kind) {
            case "max" -> 0x004D8CFF;
            case "insta" -> 0x00FF3D48;
            case "shopping" -> 0x00C05CFF;
            case "dg" -> 0x00FFC247;
            case "bg" -> 0x00FF9C38;
            default -> 0x00FFFFFF;
        };
    }

    public record DropVisual(int entityId, String kind, int round, double x, double y, double z,
                             long firstSeenAt, long expiresAt) {
    }
}

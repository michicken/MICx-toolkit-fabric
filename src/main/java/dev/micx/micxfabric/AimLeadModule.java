package dev.micx.micxfabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AimLead prediction based on positions received from the server movement packets.
 * Client interpolation history is deliberately not used as a server-position source.
 */
public final class AimLeadModule implements Module {
    private static final AimLeadModule INSTANCE = new AimLeadModule();
    private static final int SAMPLE_LIMIT = 12;
    private static final long SAMPLE_TTL_MS = 2_500L;
    private static final double SPIKE_SPEED = 8.0;
    private static final double HIDE_SPEED = 15.0;
    private static final double MIN_LEAD = 0.3;
    private static final RenderType LINE_TYPE = EspRenderTypes.espLines();

    private final AimLeadConfig config = new AimLeadConfig();
    private final Map<Integer, Track> tracks = new ConcurrentHashMap<>();
    private volatile Object activeLevel;
    private volatile int gameRtt = -1;
    private volatile int gameJitter;
    private volatile long gameAt;
    private final int[] gameRecent = new int[5];
    private int gameRecentN;
    private volatile long rttRequestAt;
    private volatile int rttRequestId;
    private volatile int nextRttId;
    private boolean enabled;

    private AimLeadModule() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    public static AimLeadModule instance() {
        return INSTANCE;
    }

    public static void recordSpawn(int id, Vec3 position) {
        INSTANCE.record(id, position, System.currentTimeMillis(), true);
    }

    public static void recordRelative(Entity entity, Vec3 position) {
        if (entity != null) INSTANCE.record(entity.getId(), position, System.currentTimeMillis(), false);
    }

    public static void recordAbsolute(int id, Vec3 position) {
        INSTANCE.record(id, position, System.currentTimeMillis(), true);
    }

    public static void recordTeleport(int id, Vec3 currentPosition, Entity entity,
                                      PositionMoveRotation change, java.util.Set<Relative> relatives) {
        if (entity == null || change == null || relatives == null) return;
        Track track = INSTANCE.tracks.get(id);
        Vec3 base = track == null ? currentPosition : track.latest();
        if (base == null) return;
        PositionMoveRotation previous = new PositionMoveRotation(base, entity.getKnownMovement(),
                entity.getYRot(), entity.getXRot());
        PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(previous, change, relatives);
        recordAbsolute(id, absolute.position());
    }

    public static void remove(int id) {
        INSTANCE.tracks.remove(id);
    }

    public static void clearTracks() {
        INSTANCE.tracks.clear();
        synchronized (INSTANCE) {
            INSTANCE.gameRtt = -1;
            INSTANCE.gameJitter = 0;
            INSTANCE.gameAt = 0L;
            java.util.Arrays.fill(INSTANCE.gameRecent, 0);
            INSTANCE.gameRecentN = 0;
        }
        INSTANCE.rttRequestAt = 0L;
        INSTANCE.lastRttRequestMs = 0L;
        INSTANCE.activeLevel = null;
    }

    @Override
    public void resetState() {
        clearTracks();
    }

    public static void recordRttResponse(int id) {
        if (!INSTANCE.enabled) return;
        if (INSTANCE.rttRequestAt != 0L && INSTANCE.rttRequestId == id) {
            long elapsed = Math.max(1L, System.nanoTime() / 1_000_000L - INSTANCE.rttRequestAt);
            INSTANCE.rttRequestAt = 0L;
            if (elapsed > 0L && elapsed < 5_000L) INSTANCE.pushGame((int) elapsed);
        }
    }

    @Override
    public String id() {
        return "aim_lead";
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
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (!enabled) clearTracks();
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public void tick(Minecraft client) {
        loadConfig();
        if (!enabled || client == null || client.level == null || client.player == null) {
            if (activeLevel != null && client != null && client.level != activeLevel) clearTracks();
            activeLevel = client == null ? null : client.level;
            return;
        }
        if (activeLevel != client.level) {
            clearTracks();
            activeLevel = client.level;
        }
        long now = System.currentTimeMillis();
        long nowMono = System.nanoTime() / 1_000_000L;
        tracks.entrySet().removeIf(entry -> {
            Track track = entry.getValue();
            Entity entity = client.level.getEntity(entry.getKey());
            return entity == null || entity.isRemoved() || now - track.latestTime() > SAMPLE_TTL_MS;
        });
        if (rttRequestAt != 0L && nowMono - rttRequestAt > 3_000L) {
            rttRequestAt = 0L;
        }
        if (config.gameRtt && config.autoPing && rttRequestAt == 0L && client.getConnection() != null
                && now - lastRttRequestMs > 1_000L) {
            requestRtt(client);
        }
    }

    private long lastRttRequestMs;

    private void requestRtt(Minecraft client) {
        rttRequestId = ++nextRttId;
        rttRequestAt = System.nanoTime() / 1_000_000L;
        lastRttRequestMs = System.currentTimeMillis();
        client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket(
                rttRequestId, "/"));
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !config.renderGhost || !config.fireDot) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return;
        GhostTarget target = selectedTarget(client);
        if (target == null || !isCrosshairHit(client, target.box())) return;
        int x = graphics.guiWidth() / 2 + config.markerOffsetX;
        int y = graphics.guiHeight() / 2 + config.markerOffsetY;
        float sx = HudLayoutRegistry.scaleX("aim_lead_marker", config.markerScaleX);
        float sy = HudLayoutRegistry.scaleY("aim_lead_marker", config.markerScaleY);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(sx, sy);
        try {
            graphics.fill(-2, 0, 3, 2, 0xFF50E68A);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void collectSubmits(LevelRenderContext context) {
        if (!enabled || !config.renderGhost) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;
        List<GhostTarget> targets = targets(client);
        if (targets.isEmpty()) return;
        SubmitNodeCollector collector = context.submitNodeCollector();
        Vec3 camera = client.gameRenderer.mainCamera().position();
        PoseStack pose = context.poseStack();
        for (GhostTarget target : targets) {
            AABB box = target.box();
            Vec3 origin = target.serverBox().getCenter();
            Vec3 ghostCenter = box.getCenter();
            if (config.serverShadow) submitBox(collector, pose, target.serverBox(), camera, 0xAA28D7E8);
            submitBox(collector, pose, box, camera, 0xAA50E68A);
            if (config.drawLink && ghostCenter.distanceToSqr(origin) >= MIN_LEAD * MIN_LEAD) {
                submitLine(collector, pose, origin.subtract(camera), ghostCenter.subtract(camera), camera, 0xCC50E68A);
            }
        }
    }

    private void submitBox(SubmitNodeCollector collector, PoseStack pose, AABB box, Vec3 camera, int color) {
        AABB local = box.move(-box.minX, -box.minY, -box.minZ);
        Vec3 offset = new Vec3(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        collector.submitShapeOutline(pose, net.minecraft.world.phys.shapes.Shapes.create(local), LINE_TYPE,
                color, 2.0f, false);
        pose.popPose();
    }

    private void submitLine(SubmitNodeCollector collector, PoseStack pose, Vec3 start, Vec3 end, Vec3 camera, int color) {
        Vector3f a = new Vector3f((float) start.x, (float) start.y, (float) start.z);
        Vector3f b = new Vector3f((float) end.x, (float) end.y, (float) end.z);
        collector.submitCustomGeometry(pose, LINE_TYPE, (stackPose, consumer) -> {
            line(consumer, stackPose, a, b, color);
        });
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Vector3f a, Vector3f b, int color) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int alpha = color >>> 24;
        consumer.addVertex(pose, a.x, a.y, a.z).setColor(red, green, blue, alpha)
                .setNormal(pose, 0, 1, 0).setLineWidth(2.0f);
        consumer.addVertex(pose, b.x, b.y, b.z).setColor(red, green, blue, alpha)
                .setNormal(pose, 0, 1, 0).setLineWidth(2.0f);
    }

    private List<GhostTarget> targets(Minecraft client) {
        if (config.zombiesOnly && !ZombiesTracker.instance().isInZombies()) return List.of();
        List<GhostTarget> result = new ArrayList<>();
        Vec3 eye = client.player.getEyePosition(1.0f);
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isTarget(living) || living.isDeadOrDying()) continue;
            double distanceSq = eye.distanceToSqr(living.getBoundingBox().getCenter());
            if (distanceSq < config.minDist * (double) config.minDist) continue;
            Track track = tracks.get(entity.getId());
            if (track == null) {
                recordAbsolute(entity.getId(), entity.getPositionCodec().getBase());
                track = tracks.get(entity.getId());
            }
            if (track == null) continue;
            Vec3 serverPosition = track.latest();
            Vec3 velocity = track.velocity();
            double speed = velocity.length();
            if (speed > HIDE_SPEED) continue;
            long now = System.currentTimeMillis();
            double tau = effectivePing() + config.extraMs;
            double seconds = clamp(tau, 50.0, 1200.0) / 1000.0;
            Vec3 predicted = serverPosition.add(velocity.scale(seconds));
            AABB serverBox = living.getBoundingBox().move(serverPosition.subtract(living.position()));
            AABB predictedBox = serverBox.move(predicted.subtract(serverPosition));
            predictedBox = clampToCollision(living, predictedBox, predicted.subtract(living.position()));
            if (predictedBox.getCenter().distanceToSqr(living.getBoundingBox().getCenter()) < MIN_LEAD * MIN_LEAD) continue;
            result.add(new GhostTarget(living, serverBox, predictedBox, velocity));
        }
        result.sort(Comparator.comparingDouble(target -> eye.distanceToSqr(target.box().getCenter())));
        return result.subList(0, Math.min(config.maxGhosts, result.size()));
    }

    /** Magnet 减速带幽灵框重建用：该实体的预测 AABB；未启用/无预测/不在候选时返回 null。 */
    public AABB leadBoxFor(LivingEntity entity) {
        if (!enabled || entity == null) return null;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return null;
        for (GhostTarget target : targets(client)) {
            if (target.entity() == entity) return target.box();
        }
        return null;
    }

    private GhostTarget selectedTarget(Minecraft client) {
        List<GhostTarget> targets = targets(client);
        if (targets.isEmpty()) return null;
        Vec3 eye = client.player.getEyePosition(1.0f);
        Vec3 look = client.player.getViewVector(1.0f);
        double best = Double.MAX_VALUE;
        GhostTarget selected = null;
        for (GhostTarget target : targets) {
            Vec3 center = target.box().getCenter();
            Vec3 delta = center.subtract(eye);
            double distance = delta.length();
            if (distance <= 0.01) continue;
            double angle = 1.0 - look.dot(delta.normalize());
            if (angle < best) {
                best = angle;
                selected = target;
            }
        }
        return selected;
    }

    private static boolean isCrosshairHit(Minecraft client, AABB box) {
        Vec3 eye = client.player.getEyePosition(1.0f);
        Vec3 end = eye.add(client.player.getViewVector(1.0f).scale(256.0));
        return box.clip(eye, end).isPresent();
    }

    private static boolean isTarget(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof WitherBoss) return false;
        return entity instanceof Enemy || entity instanceof Wolf || entity instanceof IronGolem;
    }

    private static AABB clampToCollision(Entity entity, AABB box, Vec3 delta) {
        try {
            Vec3 allowed = Entity.collideBoundingBox(entity, delta, entity.getBoundingBox(), entity.level(),
                    Entity.collectAllColliders(entity, entity.level(), entity.getBoundingBox().expandTowards(delta)));
            return entity.getBoundingBox().move(allowed);
        } catch (RuntimeException ignored) {
            return box;
        }
    }

    private void record(int id, Vec3 position, long now, boolean absolute) {
        if (!enabled || position == null) return;
        Track track = tracks.computeIfAbsent(id, ignored -> new Track());
        track.add(position, now, absolute);
    }

    private void loadConfig() {
        config.load();
    }

    public AimLeadConfig config() {
        loadConfig();
        return config;
    }

    public int markerOffsetX() { loadConfig(); return config.markerOffsetX; }
    public int markerOffsetY() { loadConfig(); return config.markerOffsetY; }
    public float markerScaleX() { loadConfig(); return config.markerScaleX; }
    public float markerScaleY() { loadConfig(); return config.markerScaleY; }

    public void setMarkerOffset(int x, int y) {
        loadConfig();
        config.markerOffsetX = Math.max(-2_000, Math.min(2_000, x));
        config.markerOffsetY = Math.max(-2_000, Math.min(2_000, y));
    }

    public void setMarkerScale(float x, float y) {
        loadConfig();
        config.markerScaleX = HudLayoutMath.clampScale(x);
        config.markerScaleY = HudLayoutMath.clampScale(y);
    }

    public void saveConfiguration() {
        loadConfig();
        config.save();
    }

    public int effectivePing() {
        if (!config.autoPing) return config.manualPing;
        if (config.gameRtt && gameRtt > 0 && gameAt > 0L
                && System.currentTimeMillis() - gameAt < 45_000L) {
            return gameRtt;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getConnection() != null && client.player != null) {
            net.minecraft.client.multiplayer.PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null) return Math.max(0, info.getLatency());
        }
        return config.manualPing;
    }

    public int gameRtt() { return gameRtt; }
    public int gameJitter() { return gameJitter; }
    public int tauMs() { return (int) clamp(effectivePing() + config.extraMs, 50, 1200); }
    public String pingSource() {
        if (config.autoPing && config.gameRtt && gameRtt > 0 && gameAt > 0L
                && System.currentTimeMillis() - gameAt < 45_000L) {
            return "game RTT";
        }
        Minecraft client = Minecraft.getInstance();
        if (config.autoPing && client != null && client.getConnection() != null && client.player != null) {
            net.minecraft.client.multiplayer.PlayerInfo info =
                    client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null && info.getLatency() > 0) return "tab";
        }
        return "manual";
    }

    /**
     * Raw RTT snapshot for timing-sensitive features.  A game RTT is trusted only
     * after three valid samples and for 45 seconds after the newest sample, which
     * matches the Forge AimLead confidence contract.  Manual/tab fallback remains
     * useful for display but is deliberately marked low-confidence.
     */
    public synchronized LatencySnapshot latencySnapshot() {
        long now = System.currentTimeMillis();
        boolean freshGame = enabled && config.autoPing && config.gameRtt
                && gameRtt > 0 && gameRecentN >= 3 && gameAt > 0L
                && now - gameAt < 45_000L;
        if (freshGame) {
            return new LatencySnapshot(gameRtt, gameJitter, "game", true, gameAt);
        }
        int fallback = Math.max(0, effectivePing());
        String source = pingSource();
        return new LatencySnapshot(fallback, 0, source, false, 0L);
    }

    private synchronized void pushGame(int sampleMs) {
        if (sampleMs <= 0 || sampleMs >= 5_000) return;
        gameRecent[gameRecentN % gameRecent.length] = sampleMs;
        gameRecentN++;
        int n = Math.min(gameRecentN, gameRecent.length);
        int[] sorted = new int[n];
        for (int i = 0; i < n; i++) sorted[i] = gameRecent[i];
        java.util.Arrays.sort(sorted);
        gameRtt = sorted[n / 2];
        gameJitter = n <= 1 ? 0 : sorted[n - 1] - sorted[0];
        gameAt = System.currentTimeMillis();
    }

    public static final class LatencySnapshot {
        public final int rttMs;
        public final int jitterMs;
        public final String source;
        public final boolean highConfidence;
        public final long sampledAt;

        private LatencySnapshot(int rttMs, int jitterMs, String source,
                                boolean highConfidence, long sampledAt) {
            this.rttMs = Math.max(0, rttMs);
            this.jitterMs = Math.max(0, jitterMs);
            this.source = source == null ? "unknown" : source;
            this.highConfidence = highConfidence;
            this.sampledAt = sampledAt;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record GhostTarget(LivingEntity entity, AABB serverBox, AABB box, Vec3 velocity) {
    }

    private static final class Track {
        private final Deque<Sample> samples = new ArrayDeque<>();

        synchronized void add(Vec3 position, long time, boolean absolute) {
            if (absolute && !samples.isEmpty() && position.distanceToSqr(samples.getLast().position()) > 4096.0) {
                samples.clear();
            }
            samples.addLast(new Sample(position, time));
            while (samples.size() > SAMPLE_LIMIT) samples.removeFirst();
            while (samples.size() > 1 && time - samples.getFirst().time() > SAMPLE_TTL_MS) samples.removeFirst();
        }

        synchronized Vec3 latest() {
            return samples.isEmpty() ? Vec3.ZERO : samples.getLast().position();
        }

        synchronized long latestTime() {
            return samples.isEmpty() ? 0L : samples.getLast().time();
        }

        synchronized Vec3 velocity() {
            if (samples.size() < 2) return Vec3.ZERO;
            List<Double> xs = new ArrayList<>();
            List<Double> ys = new ArrayList<>();
            List<Double> zs = new ArrayList<>();
            Sample previous = null;
            for (Sample sample : samples) {
                if (previous != null) {
                    double seconds = (sample.time() - previous.time()) / 1000.0;
                    if (seconds > 0.001) {
                        Vec3 delta = sample.position().subtract(previous.position());
                        double speed = delta.length() / seconds;
                        if (speed <= SPIKE_SPEED) {
                            xs.add(delta.x / seconds);
                            ys.add(delta.y / seconds);
                            zs.add(delta.z / seconds);
                        }
                    }
                }
                previous = sample;
            }
            if (xs.isEmpty()) return Vec3.ZERO;
            xs.sort(Double::compareTo);
            ys.sort(Double::compareTo);
            zs.sort(Double::compareTo);
            return new Vec3(median(xs), median(ys), median(zs));
        }

        private static double median(List<Double> values) {
            int middle = values.size() / 2;
            return values.size() % 2 == 0 ? (values.get(middle - 1) + values.get(middle)) / 2.0 : values.get(middle);
        }
    }

    private record Sample(Vec3 position, long time) {
    }
}

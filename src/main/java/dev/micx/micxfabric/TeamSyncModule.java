package dev.micx.micxfabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** TeamSync transport, snapshot HUD and ping markers for the Fabric client. */
public final class TeamSyncModule implements Module {
    public static final long SNAPSHOT_TTL_MS = 10_000L;
    public static final long TARGET_TTL_MS = 3_000L;
    private static final int TARGET_MAX_DISTANCE = 160;
    private static final TeamSyncModule INSTANCE = new TeamSyncModule();

    private final TeamSyncState state = new TeamSyncState();
    private final TeamSyncClient transport = new TeamSyncClient();
    private TeamSyncTokenProvider tokenProvider;
    private TeamSyncConfig config;
    private boolean enabled;
    private boolean configLoaded;
    private Object activeLevel;
    private boolean joined;
    private long lastRosterCheck;
    private long lastStateSend;
    private long lastHeartbeat;
    private long lastPing;
    private long sequence;
    private boolean pingWasDown;
    private boolean configDirty;
    private boolean transportStoppedForWorld;

    private TeamSyncModule() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    public static TeamSyncModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "team_sync";
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
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            tokenProvider = new TeamSyncTokenProvider(config.tokenUrl);
            transport.start(config.serverUrl, tokenProvider);
            resetTransientState();
        } else {
            shutdownSession();
        }
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return new InputBinding(config.toggleKeyCode);
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        loadConfig();
        if (newlyEnabled) return;
        config.renderOverlay = !config.renderOverlay;
        configDirty = true;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal("TeamSync HUD: "
                    + (config.renderOverlay ? "ON" : "OFF")));
        }
    }

    @Override
    public void tick(Minecraft client) {
        loadConfig();
        if (!enabled) return;
        if (client == null || client.level == null || client.player == null) {
            if (activeLevel != null) resetWorldState();
            activeLevel = null;
            joined = false;
            return;
        }
        if (transportStoppedForWorld) {
            restartTransport();
            transportStoppedForWorld = false;
        }
        if (activeLevel != null && activeLevel != client.level) {
            resetWorldState();
            restartTransport();
            transportStoppedForWorld = false;
        }
        activeLevel = client.level;
        long now = System.currentTimeMillis();
        processInbound(now);
        pruneStale(now);

        boolean pingDown = config.pingButton >= 0
                && new InputBinding(-100 + config.pingButton).down(client);
        if (pingDown && !pingWasDown && client.gui.screen() == null) sendManualPing(client, now);
        pingWasDown = pingDown;

        if (now - lastHeartbeat >= 10_000L) {
            transport.sendHeartbeat();
            lastHeartbeat = now;
        }
        if (!ZombiesTracker.instance().isInZombies()) {
            if (joined) transport.sendLeave();
            joined = false;
            state.clear();
            return;
        }
        Set<String> roster = roster(client);
        if (now - lastRosterCheck >= config.rosterCheckMs) {
            lastRosterCheck = now;
            if (roster.size() <= 1) {
                if (joined) transport.sendLeave();
                joined = false;
            } else if (!joined && transport.isConnected()) {
                joined = transport.sendJoin(selfName(client), roster, "Zombies", ZombiesTracker.instance().round());
            }
        }
        if (joined && now - lastStateSend >= config.updateIntervalMs) {
            lastStateSend = now;
            sendState(client);
        }
        if (configDirty && now - lastStateSend > 1_000L) {
            config.save();
            configDirty = false;
        }
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !config.renderOverlay) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return;
        int x = Math.max(0, graphics.guiWidth() - config.hudRightOffset);
        int y = Math.max(0, config.hudY);
        int color = transport.isConnected() ? (joined ? 0xFF57B98C : 0xFFE8A73E) : 0xFFE06A6A;
        graphics.text(minecraft.font, Component.literal("TeamSync " + (joined ? "● " + state.all().size() : "○")),
                x, y, color, true);
        y += minecraft.font.lineHeight + 2;
        if (!joined) {
            graphics.text(minecraft.font, Component.literal("等待 Zombies 队伍 / token"), x, y, 0xFF98A0AB);
            return;
        }
        List<TeamSyncSnapshot> snapshots = new ArrayList<>(state.all());
        snapshots.sort(Comparator.comparing(snapshot -> snapshot.name.toLowerCase(Locale.ROOT)));
        boolean any = false;
        long now = System.currentTimeMillis();
        for (TeamSyncSnapshot snapshot : snapshots) {
            if (snapshot.name.equals(selfName(minecraft)) || !snapshot.fresh(now, SNAPSHOT_TTL_MS)) continue;
            any = true;
            StringBuilder line = new StringBuilder(snapshot.name);
            if (config.showPing && snapshot.ping >= 0) line.append(" | ").append(snapshot.ping).append("ms");
            if (snapshot.hasTarget(now, TARGET_TTL_MS)) {
                line.append(" | -> ").append(shortType(snapshot.targetType));
            }
            graphics.text(minecraft.font, Component.literal(trim(minecraft, line.toString(), graphics.guiWidth() - x - 4)),
                    x, y, 0xFFE7EAEE);
            y += minecraft.font.lineHeight;
        }
        if (!any) graphics.text(minecraft.font, Component.literal("no fresh peers"), x, y, 0xFF5C6572);
    }

    private void collectSubmits(LevelRenderContext context) {
        if (!enabled || !config.renderWorld) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return;
        long now = System.currentTimeMillis();
        SubmitNodeCollector collector = context.submitNodeCollector();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        for (TeamSyncSnapshot snapshot : state.all()) {
            if (!snapshot.hasPing(now)) continue;
            AABB marker = new AABB(snapshot.pingX - 0.35, snapshot.pingY - 0.35, snapshot.pingZ - 0.35,
                    snapshot.pingX + 0.35, snapshot.pingY + 0.35, snapshot.pingZ + 0.35);
            submitBox(collector, context.poseStack(), marker, camera, 0xEEFFFFFF);
        }
    }

    private void submitBox(SubmitNodeCollector collector, com.mojang.blaze3d.vertex.PoseStack pose,
                           AABB box, Vec3 camera, int color) {
        AABB local = box.move(-box.minX, -box.minY, -box.minZ);
        pose.pushPose();
        pose.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);
        collector.submitShapeOutline(pose, Shapes.create(local), EspRenderTypes.espLines(), color, 2.0f, false);
        pose.popPose();
    }

    private void sendState(Minecraft minecraft) {
        Entity target = config.localAimFallback ? pickTarget(minecraft) : null;
        int targetId = target == null ? Integer.MIN_VALUE : target.getId();
        String targetType = target == null ? null : target.getType().toString();
        Vec3 targetPosition = target == null ? Vec3.ZERO : target.position();
        float targetHealth = target instanceof LivingEntity living ? living.getHealth() : -1.0f;
        int ping = 0;
        if (minecraft.getConnection() != null && minecraft.player != null) {
            var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
            if (info != null) ping = Math.max(0, info.getLatency());
        }
        transport.sendState(selfName(minecraft), minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                minecraft.player.getYRot(), minecraft.player.getXRot(), minecraft.player.getHealth(),
                minecraft.player.getMaxHealth(), minecraft.player.getAbsorptionAmount(), "unknown",
                targetId, targetType, targetPosition.x, targetPosition.y, targetPosition.z,
                targetHealth, target == null ? null : target.getName().getString(), ping, ++sequence);
    }

    private Entity pickTarget(Minecraft minecraft) {
        if (minecraft.crosshairPickEntity != null && minecraft.crosshairPickEntity != minecraft.player
                && minecraft.crosshairPickEntity instanceof LivingEntity living && !living.isDeadOrDying()) {
            return minecraft.crosshairPickEntity;
        }
        return null;
    }

    private void sendManualPing(Minecraft minecraft, long now) {
        if (now - lastPing < 500L || !transport.isConnected()) return;
        Vec3 position;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            position = minecraft.hitResult.getLocation();
        } else {
            position = minecraft.player.position().add(minecraft.player.getViewVector(1.0f).scale(TARGET_MAX_DISTANCE));
        }
        lastPing = now;
        String name = selfName(minecraft);
        transport.sendPing(name, position.x, position.y, position.z, "ping");
        TeamSyncSnapshot own = state.getOrCreate(name);
        if (own != null) {
            own.pingX = position.x;
            own.pingY = position.y;
            own.pingZ = position.z;
            own.pingLabel = "ping";
            own.pingUntilMs = now + 15_000L;
        }
    }

    private void processInbound(long now) {
        String raw;
        int budget = 256;
        while (budget-- > 0 && (raw = transport.poll()) != null) {
            if (raw.length() > 256 * 1024) continue;
            try {
                JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
                String type = text(object, "type");
                if ("room_info".equals(type)) continue;
                if ("member_left".equals(type)) {
                    state.remove(text(object, "name"));
                    continue;
                }
                if ("ping".equals(type)) {
                    TeamSyncSnapshot snapshot = state.getOrCreate(text(object, "name"));
                    if (snapshot != null) {
                        snapshot.pingX = number(object, "x");
                        snapshot.pingY = number(object, "y");
                        snapshot.pingZ = number(object, "z");
                        snapshot.pingLabel = text(object, "label");
                        snapshot.pingUntilMs = now + 15_000L;
                    }
                    continue;
                }
                if (!"state".equals(type)) continue;
                String name = text(object, "name");
                if (name == null || name.equals(selfName(Minecraft.getInstance()))) continue;
                TeamSyncSnapshot snapshot = state.getOrCreate(name);
                if (snapshot == null) continue;
                snapshot.receivedMs = now;
                JsonObject position = object.has("pos") && object.get("pos").isJsonObject()
                        ? object.getAsJsonObject("pos") : null;
                if (position != null) {
                    snapshot.posX = number(position, "x");
                    snapshot.posY = number(position, "y");
                    snapshot.posZ = number(position, "z");
                    snapshot.yaw = (float) number(position, "yaw");
                    snapshot.pitch = (float) number(position, "pitch");
                }
                snapshot.hp = (float) number(object, "hp");
                snapshot.maxHp = (float) number(object, "max");
                snapshot.absorption = (float) number(object, "abs");
                snapshot.status = sanitize(text(object, "status"), "unknown");
                snapshot.ping = (int) number(object, "ping");
                JsonObject target = object.has("target") && object.get("target").isJsonObject()
                        ? object.getAsJsonObject("target") : null;
                if (target == null) {
                    snapshot.targetId = Integer.MIN_VALUE;
                } else {
                    snapshot.targetId = target.has("id") ? target.get("id").getAsInt() : Integer.MIN_VALUE;
                    snapshot.targetType = sanitize(text(target, "type"), "?");
                    snapshot.targetX = number(target, "x");
                    snapshot.targetY = number(target, "y");
                    snapshot.targetZ = number(target, "z");
                    snapshot.targetHp = (float) number(target, "hp");
                    snapshot.targetName = sanitize(text(target, "name"), "?");
                }
            } catch (RuntimeException ignored) {
                // Malformed remote messages are discarded without surfacing payload data.
            }
        }
    }

    private void pruneStale(long now) {
        state.prune(snapshot -> snapshot.receivedMs > 0L
                && now - snapshot.receivedMs > SNAPSHOT_TTL_MS
                && !snapshot.hasPing(now));
    }

    private Set<String> roster(Minecraft minecraft) {
        Set<String> result = new HashSet<>();
        if (minecraft.level == null) return result;
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player != null && !player.isRemoved()) result.add(player.getName().getString());
        }
        return result;
    }

    private String selfName(Minecraft minecraft) {
        return minecraft == null || minecraft.player == null ? "" : minecraft.player.getName().getString();
    }

    private void resetTransientState() {
        state.clear();
        joined = false;
        lastRosterCheck = 0L;
        lastStateSend = 0L;
        lastHeartbeat = 0L;
        lastPing = 0L;
        pingWasDown = false;
        sequence = 0L;
    }

    private void shutdownSession() {
        try {
            transport.sendLeave();
        } catch (RuntimeException ignored) {
        }
        transport.stop();
        state.clear();
        resetTransientState();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        config = new TeamSyncConfig();
        config.load();
    }

    public TeamSyncConfig config() {
        loadConfig();
        return config;
    }

    public TeamSyncState state() {
        return state;
    }

    public boolean joined() {
        return joined;
    }

    public boolean connected() {
        return transport.isConnected();
    }

    public void saveConfig() {
        loadConfig();
        config.save();
        configDirty = false;
        if (enabled) restartTransport();
    }

    public void resetWorldState() {
        activeLevel = null;
        resetTransientState();
        if (!enabled) {
            transportStoppedForWorld = false;
            return;
        }
        shutdownSession();
        transportStoppedForWorld = true;
    }

    @Override
    public void resetState() {
        resetWorldState();
    }

    public void shutdown() {
        shutdownSession();
        transport.close();
        if (tokenProvider != null) {
            tokenProvider.close();
            tokenProvider = null;
        }
        transportStoppedForWorld = false;
    }

    public void restartTransport() {
        loadConfig();
        if (!enabled) return;
        TeamSyncTokenProvider previous = tokenProvider;
        if (previous != null) previous.close();
        tokenProvider = new TeamSyncTokenProvider(config.tokenUrl);
        transport.start(config.serverUrl, tokenProvider);
        resetTransientState();
    }

    private static String text(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static double number(JsonObject object, String key) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : 0.0;
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) return fallback;
        String clean = value.replaceAll("[\\p{Cntrl}§]", "").trim();
        if (clean.isBlank()) return fallback;
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }

    private static String shortType(String value) {
        if (value == null || value.isBlank()) return "?";
        int index = Math.max(value.lastIndexOf('.'), value.lastIndexOf(':'));
        return index >= 0 && index + 1 < value.length() ? value.substring(index + 1) : value;
    }

    private static String trim(Minecraft minecraft, CharSequence value, int width) {
        String text = value.toString();
        return minecraft.font.width(text) <= width ? text : minecraft.font.plainSubstrByWidth(text, Math.max(1, width), true);
    }
}

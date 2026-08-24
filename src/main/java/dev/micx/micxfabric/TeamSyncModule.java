package dev.micx.micxfabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TeamSync transport, snapshot HUD and ping markers for the Fabric client. */
public final class TeamSyncModule implements Module {
    public static final long SNAPSHOT_TTL_MS = 10_000L;
    public static final long TARGET_TTL_MS = 3_000L;
    private static final int TARGET_MAX_DISTANCE = 160;
    private static final long JOIN_ACK_TIMEOUT_MS = 4_000L;
    /** 入房后延迟公布共享玩家列表，等服务端把同房队友聚齐。 */
    private static final long ROOM_ANNOUNCE_DELAY_MS = 2_000L;
    private static final TeamSyncModule INSTANCE = new TeamSyncModule();

    /**
     * 自己释放 LR 的聊天事件（Hypixel 实测格式）："You struck 4 enemies with your
     * Lightning Rod Skill!" 或 "You struck Worm with your Lightning Rod Skill!"。
     */
    private static final Pattern LR_RELEASE_CHAT =
            Pattern.compile("^You struck (.+) with your Lightning Rod Skill!$");

    private final TeamSyncState state = new TeamSyncState();
    private final TeamSyncClient transport = new TeamSyncClient();
    private final TeamSkillTracker localSkillTracker = new TeamSkillTracker();
    private final Set<String> roomMembers = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> lastRoster = new HashSet<>();
    private final Map<String, String> skillCooldownAnnounced = new HashMap<>();
    private TeamSyncTokenProvider tokenProvider;
    private TeamSyncConfig config;
    private boolean enabled;
    private boolean configLoaded;
    private Object activeLevel;
    private Object skillLevel;
    private boolean joined;
    private boolean roomJoinAnnounced;
    private long roomJoinAnnounceAt;
    private long pendingJoinAt;
    private long lastRosterCheck;
    private long lastStateSend;
    private long lastHeartbeat;
    private long lastPing;
    private long sequence;
    private boolean pingWasDown;
    private String lastSentH7Item, lastSentH7Name, lastSentH8Item, lastSentH8Name, lastSentH9Item, lastSentH9Name;
    private boolean configDirty;
    private boolean transportStoppedForWorld;

    private TeamSyncModule() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, boundType, receptionTime) -> {
            if (message == null) return;
            onChatText(message.getString());
        });
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
            client.player.sendSystemMessage(ChatMessageStyles.notice("TeamSync HUD: "
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
            return;
        }
        if (transportStoppedForWorld) {
            restartTransport();
            transportStoppedForWorld = false;
        }
        if (activeLevel != null && activeLevel != client.level) {
            resetWorldState();
        }
        activeLevel = client.level;
        long now = System.currentTimeMillis();

        // 重连后服务端没有会话：只清本地会话态，随后立刻重新 join（绝不能 sendLeave）
        if (transport.consumeReconnected()) {
            resetSession(false);
            lastRosterCheck = 0L;
        }

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
            resetSession(true);
            return;
        }
        // 延迟公布共享玩家列表：等 room 成员聚齐再打印（避免开局只有自己的假象）
        String self = selfName(client);
        if (joined && !roomJoinAnnounced && roomJoinAnnounceAt > 0L && now >= roomJoinAnnounceAt) {
            roomJoinAnnounced = true;
            roomJoinAnnounceAt = 0L;
            announceRoomJoin(client, self);
        }
        Set<String> roster = roster(client);
        if (now - lastRosterCheck >= config.rosterCheckMs) {
            lastRosterCheck = now;
            checkRosterAndJoin(client, roster, self, now);
        }
        if (joined && roomMembers.contains(self) && now - lastStateSend >= config.updateIntervalMs) {
            lastStateSend = now;
            sendState(client, self);
        }
        if (configDirty && now - lastStateSend > 1_000L) {
            config.save();
            configDirty = false;
        }
    }

    // ---- 会话生命周期（Forge checkRosterAndJoin / resetSession 语义） ----

    private void checkRosterAndJoin(Minecraft client, Set<String> roster, String self, long now) {
        boolean solo = roster.size() <= 1 || (roster.size() == 1 && roster.contains(self));
        if (solo) {
            if (joined || pendingJoinAt > 0L || !roomMembers.isEmpty() || !lastRoster.isEmpty()) {
                resetSession(true);
            }
            return;
        }
        if (joined && !roomMembers.contains(self)) {
            resetSession(false);   // join ack 丢失
        }
        if (pendingJoinAt > 0L && now - pendingJoinAt < JOIN_ACK_TIMEOUT_MS) return;
        if (joined && roster.equals(lastRoster)) return;
        if (!roster.equals(lastRoster)) {
            if (joined) resetSession(true);
            lastRoster.clear();
            lastRoster.addAll(roster);
        }
        int round = ZombiesTracker.instance().round();
        if (transport.sendJoin(self, roster,
                ZombiesTracker.instance().isInAlienArcadium() ? "AA" : "Zombies", round)) {
            joined = false;   // room_info 才是入房确认
            pendingJoinAt = now;
        } else {
            pendingJoinAt = 0L;
        }
    }

    /** 清一次对局会话；保留 WebSocket 连接供下一局复用。 */
    private void resetSession(boolean notifyServer) {
        boolean hadSession = joined || pendingJoinAt > 0L || !roomMembers.isEmpty()
                || !lastRoster.isEmpty() || !state.all().isEmpty();
        if (!hadSession) return;
        if (notifyServer) {
            try { transport.sendLeave(); } catch (RuntimeException ignored) { }
        }
        joined = false;
        pendingJoinAt = 0L;
        lastRoster.clear();
        roomMembers.clear();
        state.clear();
        skillCooldownAnnounced.clear();
        roomJoinAnnounced = false;
        roomJoinAnnounceAt = 0L;
        localSkillTracker.reset();
        skillLevel = null;
        lastStateSend = 0L;
        lastRosterCheck = 0L;
    }

    // ---- LR 聊天事件（自己释放） ----

    private void onChatText(String raw) {
        if (!enabled) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        try {
            Matcher matcher = LR_RELEASE_CHAT.matcher(raw.trim());
            if (!matcher.matches()) return;
            String target = matcher.group(1).trim();
            Matcher count = Pattern.compile("(\\d+).*").matcher(target);
            int struckCount = count.matches() ? Integer.parseInt(count.group(1)) : -1;
            String struckName = count.matches() ? null : target;

            long now = System.currentTimeMillis();
            localSkillTracker.markReleased("Lightning Rod", now);
            String self = selfName(client);
            announceLr(client, self, struckCount, struckName);
            transport.sendLrRelease(self, struckCount, struckName);
            lastStateSend = 0L;   // 下个 tick 立即带上 lr 精确倒计时
        } catch (RuntimeException ignored) { }
    }

    private void announceRoomJoin(Minecraft client, String self) {
        if (client.player == null || roomMembers.isEmpty()) return;
        List<String> sorted = new ArrayList<>(roomMembers);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        client.player.sendSystemMessage(Component.literal(
                "§6[MICx] §e本局有 §b" + sorted.size() + " §e位玩家共享技能：§f" + String.join(", ", sorted)));
    }

    private void announceLr(Minecraft client, String name, int struckCount, String struckName) {
        if (client == null || client.player == null) return;
        StringBuilder sb = new StringBuilder("§6[MICx] §f").append(name).append("§e 使用了LR命中了 ");
        if (struckCount >= 0) sb.append("§c").append(struckCount).append(" 个敌人");
        else if (struckName != null) sb.append("§d").append(struckName);
        client.player.sendSystemMessage(Component.literal(sb.toString()));
    }

    private void announceSkillCooldownSoon(Minecraft client, String playerName, String skillName, int remainingS) {
        if (client == null || client.player == null) return;
        client.player.sendSystemMessage(Component.literal("§6[TeamSync] §f" + playerName
                + "§e 的 §b" + skillName + "§e 冷却剩余 §c" + remainingS + " 秒"));
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        loadConfig();
        if (!enabled || !config.renderOverlay) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return;
        float scaleX = HudLayoutRegistry.scaleX("team_sync", config.hudScaleX);
        float scaleY = HudLayoutRegistry.scaleY("team_sync", config.hudScaleY);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            int x = Math.max(0, Math.round((graphics.guiWidth() - config.hudRightOffset) / scaleX));
            int y = Math.max(0, Math.round(config.hudY / scaleY));
            int color = transport.isConnected() ? (joined ? 0xFF57B98C : 0xFFE8A73E) : 0xFFE06A6A;
            graphics.text(minecraft.font, Component.literal("TeamSync " + (joined ? "● " + state.all().size() : "○")),
                    x, y, color, true);
            y += Math.round((minecraft.font.lineHeight + 2) / scaleY);
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
                graphics.text(minecraft.font, Component.literal(trim(minecraft, line.toString(),
                                Math.max(20, Math.round((graphics.guiWidth() - x * scaleX - 4) / scaleX)))),
                        x, y, 0xFFE7EAEE);
                y += Math.max(1, Math.round(minecraft.font.lineHeight / scaleY));
            }
            if (!any) graphics.text(minecraft.font, Component.literal("no fresh peers"), x, y, 0xFF5C6572);
        } finally {
            graphics.pose().popMatrix();
        }
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

    private void sendState(Minecraft minecraft, String self) {
        Entity target = pickAimTarget(minecraft);
        int targetId = target == null ? Integer.MIN_VALUE : target.getId();
        String targetType = target == null ? null : target.getType().toString();
        Vec3 targetPosition = target == null ? Vec3.ZERO : target.position();
        float targetHealth = target instanceof LivingEntity living ? living.getHealth() : -1.0f;
        int ping = 0;
        if (minecraft.getConnection() != null) {
            var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
            if (info != null) ping = Math.max(0, info.getLatency());
        }
        // 状态上报走计分板状态机（alive/down/dead/quit），不再发 "unknown"
        String status = ZombiesTracker.instance().playerStatus(self);
        if (status == null || status.isBlank()) status = "alive";

        // 本地技能采样 + LR 精确通道（仅本地槽位 5 是 Lightning Rod 时携带）
        TeamSkillTracker.Snapshot skill = observeLocalSkill(minecraft);
        boolean includeLr = skill != null && skill.known && "Lightning Rod".equals(skill.skillName);
        long lrReleasedAt = includeLr ? localSkillTracker.releasedAtMs() : 0L;
        long lrReadyAt = includeLr ? localSkillTracker.cooldownUntilMs() : 0L;

        String curH7Item = hotbarItemId(minecraft.player.getInventory().getItem(6));
        String curH7Name = hotbarDisplayName(minecraft.player.getInventory().getItem(6));
        String curH8Item = hotbarItemId(minecraft.player.getInventory().getItem(7));
        String curH8Name = hotbarDisplayName(minecraft.player.getInventory().getItem(7));
        String curH9Item = hotbarItemId(minecraft.player.getInventory().getItem(8));
        String curH9Name = hotbarDisplayName(minecraft.player.getInventory().getItem(8));
        String sendH7Item = null, sendH7Name = null, sendH8Item = null, sendH8Name = null, sendH9Item = null, sendH9Name = null;
        boolean h7Changed = curH7Item != null && (!curH7Item.equals(lastSentH7Item) || !java.util.Objects.equals(curH7Name, lastSentH7Name));
        boolean h8Changed = curH8Item != null && (!curH8Item.equals(lastSentH8Item) || !java.util.Objects.equals(curH8Name, lastSentH8Name));
        boolean h9Changed = curH9Item != null && (!curH9Item.equals(lastSentH9Item) || !java.util.Objects.equals(curH9Name, lastSentH9Name));
        if (h7Changed) { sendH7Item = curH7Item; sendH7Name = curH7Name; }
        if (h8Changed) { sendH8Item = curH8Item; sendH8Name = curH8Name; }
        if (h9Changed) { sendH9Item = curH9Item; sendH9Name = curH9Name; }
        boolean accepted = transport.sendState(self, minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                minecraft.player.getYRot(), minecraft.player.getXRot(), minecraft.player.getHealth(),
                minecraft.player.getMaxHealth(), minecraft.player.getAbsorptionAmount(), status,
                targetId, targetType, targetPosition.x, targetPosition.y, targetPosition.z,
                targetHealth, target == null ? null : target.getName().getString(), ping, ++sequence,
                skill, includeLr, lrReleasedAt, lrReadyAt,
                sendH7Item, sendH7Name, sendH8Item, sendH8Name, sendH9Item, sendH9Name);
        if (accepted) {
            if (h7Changed) { lastSentH7Item = sendH7Item; lastSentH7Name = sendH7Name; }
            if (h8Changed) { lastSentH8Item = sendH8Item; lastSentH8Name = sendH8Name; }
            if (h9Changed) { lastSentH9Item = sendH9Item; lastSentH9Name = sendH9Name; }
        }
    }

    /** 本地槽位 5 技能采样（世界切换时重置；供 TeammateHP 自己那一行复用）。 */
    public TeamSkillTracker.Snapshot observeLocalSkill(Minecraft minecraft) {
        if (skillLevel != minecraft.level) {
            skillLevel = minecraft.level;
            localSkillTracker.reset();
        }
        ItemStack stack = minecraft.player.getInventory().getItem(TeamSkillTracker.SLOT_INDEX);
        String ready = null;
        boolean gray = false;
        int count = -1;
        if (stack != null && !stack.isEmpty()) {
            ready = stack.getHoverName().getString();
            count = stack.getCount();
            // 冷却时 Hypixel 把物品换成 dye（名字不变）；26.2 映射为 light_gray_dye 等 DyeItem
            gray = stack.getItem() instanceof net.minecraft.world.item.DyeItem;
        }
        localSkillTracker.observe(ready == null ? null : TeamSkillTracker.canonicalSkillName(ready), gray, count,
                System.currentTimeMillis());
        return localSkillTracker.snapshot(System.currentTimeMillis());
    }

    /** 本地技能状态（TeammateHP 自己那行用；LR 精确倒计时优先于灰色染料估算）。 */
    public TeamSkillTracker.Snapshot localSkillSnapshot(long now) {
        return localSkillTracker.snapshot(now);
    }

    /**
     * 独立长距选靶：从眼睛沿视线 ray-AABB（含墙体遮挡），返回准星最贴合的敌对活体。
     * 替代 crosshairPickEntity 的原版 3~5 格限制（枪战永远够不到远怪）。
     */
    private Entity pickAimTarget(Minecraft minecraft) {
        if (!config.localAimFallback) return null;
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) return null;
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 reach = eye.add(look.scale(TARGET_MAX_DISTANCE));
        BlockHitResult wall = minecraft.level.clip(new ClipContext(eye, reach,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDist = wall.getType() == HitResult.Type.MISS
                ? TARGET_MAX_DISTANCE
                : wall.getLocation().distanceTo(eye);
        double best = maxDist * maxDist;
        Entity found = null;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == player || living instanceof Player || living instanceof ArmorStand) continue;
            if (!living.isAlive()) continue;
            if (!isHostileType(living)) continue;
            AABB realBox = living.getBoundingBox();
            if (realBox.contains(eye)) return living;   // 贴脸：眼睛已在 hitbox 内
            var hit = realBox.inflate(0.3, 0.3, 0.3).clip(eye, reach);
            if (hit.isEmpty()) continue;
            double d2 = eye.distanceToSqr(hit.get());
            Vec3 closestPoint = closestPoint(realBox, hit.get());
            if (d2 < best && hasClearPath(minecraft, eye, closestPoint)) {
                best = d2;
                found = living;
            }
        }
        return found;
    }

    private static Vec3 closestPoint(AABB box, Vec3 point) {
        return new Vec3(
                Math.max(box.minX, Math.min(box.maxX, point.x)),
                Math.max(box.minY, Math.min(box.maxY, point.y)),
                Math.max(box.minZ, Math.min(box.maxZ, point.z)));
    }

    private static boolean hasClearPath(Minecraft minecraft, Vec3 eye, Vec3 target) {
        BlockHitResult obstruction = minecraft.level.clip(new ClipContext(eye, target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        return obstruction.getType() != HitResult.Type.BLOCK
                || eye.distanceToSqr(obstruction.getLocation()) + 0.01 >= eye.distanceToSqr(target);
    }

    /** 可作为"瞄准目标"的敌对活体：Monster（僵尸/巨人/小丑/史莱姆）+ 铁傀儡 + 狼。 */
    static boolean isHostileType(LivingEntity living) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getPath();
        return living instanceof Monster || "iron_golem".equals(path) || "wolf".equals(path);
    }

    private static String hotbarItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key != null) return key.toString();
        } catch (Throwable ignored) { }
        return null;
    }

    private static String hotbarDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try { return stack.getHoverName().getString(); } catch (Throwable ignored) { return null; }
    }

    private void sendManualPing(Minecraft minecraft, long now) {
        if (now - lastPing < 500L || !transport.isConnected()) return;
        Vec3 position;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) {
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
                if ("room_info".equals(type)) {
                    roomMembers.clear();
                    JsonArray members = object.has("members") && object.get("members").isJsonArray()
                            ? object.getAsJsonArray("members") : null;
                    if (members != null) {
                        for (var element : members) {
                            String member = sanitize(element.isJsonNull() ? null : element.getAsString(), null);
                            if (member != null) roomMembers.add(member);
                        }
                    }
                    String self = selfName(Minecraft.getInstance());
                    boolean confirmed = !self.isEmpty() && roomMembers.contains(self);
                    joined = confirmed;
                    if (confirmed) {
                        pendingJoinAt = 0L;
                        if (!roomJoinAnnounced && roomJoinAnnounceAt == 0L) {
                            roomJoinAnnounceAt = System.currentTimeMillis() + ROOM_ANNOUNCE_DELAY_MS;
                        }
                    }
                    // 房间外快照清理
                    for (TeamSyncSnapshot snapshot : state.all()) {
                        if (snapshot.name.equals(self)) continue;
                        if (!roomMembers.contains(snapshot.name)) state.remove(snapshot.name);
                    }
                    continue;
                }
                if ("member_left".equals(type)) {
                    String name = text(object, "name");
                    if (name == null) continue;
                    if (name.equals(selfName(Minecraft.getInstance()))) {
                        resetSession(false);
                        lastRosterCheck = 0L;
                    } else {
                        roomMembers.remove(name);
                        state.remove(name);
                    }
                    continue;
                }
                if ("lr_release".equals(type)) {
                    // 队友释放 LR：聊天提示 + 命中徽章落盘（TeammateHP 6s 内展示 ×N / ·名）
                    String name = text(object, "name");
                    if (name == null || name.equals(selfName(Minecraft.getInstance()))) continue;
                    int struckCount = object.has("struck_count") && object.get("struck_count").isJsonPrimitive()
                            ? object.get("struck_count").getAsInt() : -1;
                    String struckName = text(object, "struck_name");
                    TeamSyncSnapshot peer = state.getOrCreate(name);
                    if (peer != null) {
                        peer.lastLrStruckCount = struckCount;
                        peer.lastLrStruckName = struckName;
                        peer.lastLrHitAtMs = System.currentTimeMillis();
                    }
                    announceLr(Minecraft.getInstance(), name, struckCount, struckName);
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
                    snapshot.targetId = target.has("id") && target.get("id").isJsonPrimitive()
                            ? target.get("id").getAsInt() : Integer.MIN_VALUE;
                    snapshot.targetType = sanitize(text(target, "type"), "?");
                    snapshot.targetX = number(target, "x");
                    snapshot.targetY = number(target, "y");
                    snapshot.targetZ = number(target, "z");
                    snapshot.targetHp = (float) number(target, "hp");
                    snapshot.targetName = sanitize(text(target, "name"), "?");
                }
                applySkillState(name, snapshot, object, now);
                applyLrChannel(snapshot, object, now);
                maybeAnnounceSkillCooldownSoon(Minecraft.getInstance(), name, snapshot);
                if (object.has("hotbar") && object.get("hotbar").isJsonObject()) {
                    JsonObject hotbar = object.getAsJsonObject("hotbar");
                    for (String k : new String[]{"7","8","9"}) {
                        if (!hotbar.has(k) || !hotbar.get(k).isJsonObject()) continue;
                        JsonObject slot = hotbar.getAsJsonObject(k);
                        String item = sanitize(text(slot, "item"), null);
                        String hbName = sanitize(text(slot, "name"), null);
                        if (item == null && hbName == null) continue;
                        if ("7".equals(k)) { snapshot.hotbar7Item = item; snapshot.hotbar7Name = hbName; }
                        else if ("8".equals(k)) { snapshot.hotbar8Item = item; snapshot.hotbar8Name = hbName; }
                        else { snapshot.hotbar9Item = item; snapshot.hotbar9Name = hbName; }
                        snapshot.hotbarUpdatedMs = now;
                    }
                }
            } catch (RuntimeException ignored) {
                // Malformed remote messages are discarded without surfacing payload data.
            }
        }
    }

    /** 技能通道应用（协议同 Forge）：字段缺失或非法时清空陈旧技能。 */
    private void applySkillState(String peerName, TeamSyncSnapshot snapshot, JsonObject object, long now) {
        boolean applied = false;
        if (object.has("skill") && object.get("skill").isJsonObject()) {
            JsonObject skill = object.getAsJsonObject("skill");
            int slot = skill.has("slot") && skill.get("slot").isJsonPrimitive() ? skill.get("slot").getAsInt() : -1;
            if (slot == TeamSkillTracker.SLOT_INDEX) {
                String rawState = sanitize(text(skill, "state"), "");
                String nameValue = sanitize(text(skill, "name"), null);
                String canonical = TeamSkillTracker.canonicalSkillName(nameValue);
                int remaining = (int) number(skill, "remaining_s");
                if (TeamSkillTracker.State.UNKNOWN.name().equals(rawState)) {
                    snapshot.clearSkill(now);
                    applied = true;
                } else if (remaining >= 0 && remaining <= 120
                        && (TeamSkillTracker.State.READY.name().equals(rawState)
                        || TeamSkillTracker.State.COOLING.name().equals(rawState))
                        && canonical != null) {
                    snapshot.skillKnown = true;
                    snapshot.skillState = TeamSkillTracker.State.valueOf(rawState);
                    snapshot.skillName = canonical;
                    snapshot.skillRemainingSeconds =
                            snapshot.skillState == TeamSkillTracker.State.COOLING ? remaining : 0;
                    snapshot.skillUpdatedMs = now;
                    applied = true;
                }
            }
        }
        if (!applied) snapshot.clearSkill(now);
    }

    /** LR 释放通道应用：缺失即清除陈旧冷却。 */
    private void applyLrChannel(TeamSyncSnapshot snapshot, JsonObject object, long now) {
        if (object.has("lr") && object.get("lr").isJsonObject()
                && object.getAsJsonObject("lr").has("ready_at")) {
            JsonObject lr = object.getAsJsonObject("lr");
            snapshot.lrKnown = true;
            snapshot.lrReleasedAtMs = (long) number(lr, "released");
            snapshot.lrReadyAtMs = (long) number(lr, "ready_at");
            snapshot.lrUpdatedMs = now;
        } else {
            snapshot.clearLr(now);
        }
    }

    /** 队友技能冷却"剩5秒"聊天提醒：冷却从 >5 入 ≤5 触发一次（就绪/换技能重置）。 */
    private void maybeAnnounceSkillCooldownSoon(Minecraft client, String playerName, TeamSyncSnapshot snapshot) {
        if (!snapshot.skillKnown || snapshot.skillName == null) {
            skillCooldownAnnounced.remove(playerName);
            return;
        }
        if (snapshot.skillState != TeamSkillTracker.State.COOLING) {
            skillCooldownAnnounced.remove(playerName);
            return;
        }
        String announced = skillCooldownAnnounced.get(playerName);
        if (announced != null && announced.equals(snapshot.skillName)) return;
        if (snapshot.skillRemainingSeconds <= 0 || snapshot.skillRemainingSeconds > 5) return;
        skillCooldownAnnounced.put(playerName, snapshot.skillName);
        announceSkillCooldownSoon(client, playerName, snapshot.skillName, snapshot.skillRemainingSeconds);
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
        roomMembers.clear();
        lastRoster.clear();
        skillCooldownAnnounced.clear();
        roomJoinAnnounced = false;
        roomJoinAnnounceAt = 0L;
        pendingJoinAt = 0L;
        joined = false;
        lastRosterCheck = 0L;
        lastStateSend = 0L;
        lastHeartbeat = 0L;
        lastPing = 0L;
        pingWasDown = false;
        sequence = 0L;
        localSkillTracker.reset();
        skillLevel = null;
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
        resetSession(true);
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

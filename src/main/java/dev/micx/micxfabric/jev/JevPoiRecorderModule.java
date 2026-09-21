package dev.micx.micxfabric.jev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.micx.micxfabric.ChatMessageStyles;
import dev.micx.micxfabric.FabricRuntime;
import dev.micx.micxfabric.InputBinding;
import dev.micx.micxfabric.MicxFabric;
import dev.micx.micxfabric.Module;
import dev.micx.micxfabric.ModuleStateStore;
import dev.micx.micxfabric.PacketLogModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

/** Human-demonstration recorder for semantic Zombies points of interest. It never replays actions. */
public final class JevPoiRecorderModule implements Module {
    private static final JevPoiRecorderModule INSTANCE = new JevPoiRecorderModule();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Mac-friendly dedicated recorder key: the backslash key (\\). */
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_BACKSLASH;
    private static final int MAX_EVENTS = 4_096;
    private static final double HOLOGRAM_RANGE = 36.0;
    /** Keep semantic evidence, not high-rate movement/keepalive noise. */
    private static final Set<String> SEMANTIC_PACKET_NAMES = Set.of(
            "ServerboundUseItemOnPacket", "ServerboundUseItemPacket", "ServerboundInteractPacket",
            "ServerboundContainerClickPacket", "ServerboundContainerClosePacket",
            "ClientboundOpenScreenPacket", "ClientboundContainerSetContentPacket",
            "ClientboundContainerSetSlotPacket", "ClientboundContainerClosePacket",
            "ClientboundSystemChatPacket", "ClientboundSetTitleTextPacket",
            "ClientboundSetSubtitleTextPacket", "ClientboundSetActionBarTextPacket",
            "ClientboundSoundPacket", "ClientboundBlockChangedAckPacket",
            "ClientboundContainerClosePacket");
    private static final Set<String> DEDUP_INBOUND_PACKET_NAMES = Set.of(
            "ClientboundSystemChatPacket", "ClientboundSetTitleTextPacket",
            "ClientboundSetSubtitleTextPacket", "ClientboundSetActionBarTextPacket",
            "ClientboundSoundPacket", "ClientboundContainerSetContentPacket",
            "ClientboundContainerSetSlotPacket", "ClientboundContainerClosePacket",
            "ClientboundBlockChangedAckPacket");

    private final Queue<JsonObject> networkEvents = new ConcurrentLinkedQueue<>();
    private boolean enabled;
    private volatile boolean recording;
    private String nextLabel = "";
    private JsonObject current;
    private String lastAimSignature = "";
    private int droppedEvents;
    private int dedupedEvents;
    private final Set<String> seenInboundEvidence = ConcurrentHashMap.newKeySet();

    private JevPoiRecorderModule() { }
    public static JevPoiRecorderModule instance() { return INSTANCE; }
    @Override public String id() { return "jev_poi_recorder"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean value) {
        if (!value && recording) cancel("module-disabled");
        enabled = value;
        ModuleStateStore.put(id(), value);
    }

    @Override public InputBinding primaryBinding() { return new InputBinding(DEFAULT_KEY); }

    @Override public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (client == null || client.player == null || client.level == null) return;
        if (recording) finish(client); else begin(client);
    }

    @Override public void tick(Minecraft client) {
        if (!recording || current == null) return;
        drainNetworkEvents();
        captureAim(client);
    }

    @Override public void resetState() { if (recording) cancel("world-reset"); }

    /** Set with /micx poi label <name>; unlabelled captures remain intentionally unusable. */
    public void setNextLabel(String label) { nextLabel = sanitizeLabel(label); }
    public String nextLabel() { return nextLabel.isBlank() ? "(unlabelled)" : nextLabel; }
    public String status() {
        if (!recording || current == null) return "idle label=" + nextLabel();
        return "recording " + current.get("id").getAsString() + " label=" + current.get("label").getAsString();
    }

    /** Called by Connection mixin. Network-thread safe: it only creates immutable metadata and queues it. */
    public void capturePacket(boolean outbound, Packet<?> packet) {
        if (!recording || packet == null) return;
        if (!SEMANTIC_PACKET_NAMES.contains(packet.getClass().getSimpleName())) return;
        JsonObject event = new JsonObject();
        event.addProperty("at", System.currentTimeMillis());
        event.addProperty("direction", outbound ? "out" : "in");
        event.addProperty("packet", packet.getClass().getSimpleName());
        event.addProperty("summary", PacketLogModule.describe(packet));
        if (packet instanceof ServerboundUseItemOnPacket use) {
            BlockHitResult hit = use.getHitResult();
            if (hit != null) {
                JsonObject target = new JsonObject();
                target.addProperty("x", hit.getBlockPos().getX());
                target.addProperty("y", hit.getBlockPos().getY());
                target.addProperty("z", hit.getBlockPos().getZ());
                target.addProperty("face", hit.getDirection().getName());
                target.addProperty("hit_x", round(hit.getLocation().x));
                target.addProperty("hit_y", round(hit.getLocation().y));
                target.addProperty("hit_z", round(hit.getLocation().z));
                event.add("block_target", target);
            }
        } else if (packet instanceof ServerboundInteractPacket interact) {
            event.addProperty("entity_id", interact.entityId());
            event.addProperty("hand", String.valueOf(interact.hand()));
            event.addProperty("secondary", interact.usingSecondaryAction());
            if (interact.location() != null) {
                event.addProperty("hit_x", round(interact.location().x));
                event.addProperty("hit_y", round(interact.location().y));
                event.addProperty("hit_z", round(interact.location().z));
            }
        } else if (packet instanceof ServerboundContainerClickPacket click) {
            event.addProperty("container_id", click.containerId());
            event.addProperty("state_id", click.stateId());
            event.addProperty("slot", click.slotNum());
            event.addProperty("button", click.buttonNum());
            event.addProperty("input", String.valueOf(click.containerInput()));
        } else if (packet instanceof ClientboundOpenScreenPacket open) {
            event.addProperty("container_id", open.getContainerId());
            event.addProperty("container_type", String.valueOf(open.getType()));
            event.addProperty("title", open.getTitle().getString());
        } else if (packet instanceof ClientboundContainerSetContentPacket content) {
            event.addProperty("container_id", content.containerId());
            event.add("slots", itemSlots(content.items()));
        }
        if (!outbound && DEDUP_INBOUND_PACKET_NAMES.contains(packet.getClass().getSimpleName())) {
            String signature = packet.getClass().getSimpleName() + "|" + event;
            if (!seenInboundEvidence.add(signature)) {
                dedupedEvents++;
                return;
            }
        }
        networkEvents.add(event);
    }

    private void begin(Minecraft client) {
        networkEvents.clear();
        long now = System.currentTimeMillis();
        current = new JsonObject();
        current.addProperty("schema", "jev-poi-recording-v1");
        current.addProperty("id", "poi-" + now);
        current.addProperty("label", nextLabel);
        current.addProperty("label_required", nextLabel.isBlank());
        current.addProperty("started_at", now);
        current.add("anchor", playerSnapshot(client));
        current.add("nearby_holograms", holograms(client));
        current.add("events", new JsonArray());
        droppedEvents = 0;
        dedupedEvents = 0;
        seenInboundEvidence.clear();
        lastAimSignature = "";
        recording = true;
        append("recording_started", "Human demonstration started; recorder sends no packets.");
        captureAim(client);
        client.player.sendSystemMessage(ChatMessageStyles.notice("POI 录制开始（反斜杠键结束）· 标签=" + nextLabel()));
    }

    private void finish(Minecraft client) {
        if (current == null) return;
        drainNetworkEvents();
        captureAim(client);
        current.addProperty("ended_at", System.currentTimeMillis());
        current.add("end_anchor", playerSnapshot(client));
        current.add("end_nearby_holograms", holograms(client));
        current.addProperty("event_limit", MAX_EVENTS);
        current.addProperty("dropped_events", droppedEvents);
        current.addProperty("deduped_events", dedupedEvents);
        append("recording_finished", "Human demonstration finished.");
        recording = false;
        Path saved = save(current);
        String label = current.get("label").getAsString();
        current = null;
        if (saved == null) client.player.sendSystemMessage(ChatMessageStyles.warning("POI 录制保存失败，查看日志"));
        else if (label.isBlank()) client.player.sendSystemMessage(ChatMessageStyles.warning(
                "POI 已保存但未命名，Jev 不会使用。下次先 /micx poi label <名称>"));
        else client.player.sendSystemMessage(ChatMessageStyles.notice("POI 录制已保存：" + saved.getFileName()));
        nextLabel = "";
    }

    private void cancel(String reason) {
        recording = false; current = null; networkEvents.clear(); lastAimSignature = "";
        droppedEvents = 0; dedupedEvents = 0; seenInboundEvidence.clear();
        MicxFabric.LOGGER.info("Discarded active Jev POI recording: {}", reason);
    }

    private void drainNetworkEvents() { JsonObject event; while ((event = networkEvents.poll()) != null) append(event); }

    private void captureAim(Minecraft client) {
        if (client == null || client.hitResult == null) return;
        HitResult hit = client.hitResult;
        JsonObject aimed = new JsonObject();
        aimed.addProperty("hit_type", hit.getType().name());
        aimed.addProperty("x", round(hit.getLocation().x)); aimed.addProperty("y", round(hit.getLocation().y)); aimed.addProperty("z", round(hit.getLocation().z));
        if (hit instanceof BlockHitResult block) {
            aimed.addProperty("block", block.getBlockPos().toShortString()); aimed.addProperty("face", block.getDirection().getName());
        } else if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            aimed.addProperty("entity_id", entity.getId()); aimed.addProperty("entity_type", String.valueOf(entity.getType()));
            aimed.addProperty("entity_name", entity.getName().getString());
            aimed.addProperty("entity_x", round(entity.getX())); aimed.addProperty("entity_y", round(entity.getY())); aimed.addProperty("entity_z", round(entity.getZ()));
        }
        String signature = aimed.toString();
        if (!signature.equals(lastAimSignature)) {
            lastAimSignature = signature;
            JsonObject event = new JsonObject(); event.addProperty("at", System.currentTimeMillis()); event.addProperty("kind", "aim_changed"); event.add("target", aimed); append(event);
        }
    }

    private void append(String kind, String detail) { JsonObject e = new JsonObject(); e.addProperty("at", System.currentTimeMillis()); e.addProperty("kind", kind); e.addProperty("detail", detail); append(e); }
    private void append(JsonObject event) {
        if (current == null) return;
        JsonArray events = current.getAsJsonArray("events");
        if (events.size() >= MAX_EVENTS) {
            droppedEvents++;
            // The terminal marker is useful even when a very long demonstration filled the cap.
            if (!"recording_finished".equals(event.has("kind") ? event.get("kind").getAsString() : "")) return;
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).isJsonObject() && events.get(i).getAsJsonObject().has("packet")) {
                    events.remove(i);
                    break;
                }
            }
        }
        events.add(event);
    }

    private static JsonObject playerSnapshot(Minecraft client) {
        JsonObject anchor = new JsonObject(); if (client == null || client.player == null) return anchor;
        anchor.addProperty("x", round(client.player.getX())); anchor.addProperty("y", round(client.player.getY())); anchor.addProperty("z", round(client.player.getZ()));
        anchor.addProperty("yaw", round(client.player.getYRot())); anchor.addProperty("pitch", round(client.player.getXRot())); return anchor;
    }

    private static JsonArray holograms(Minecraft client) {
        JsonArray out = new JsonArray(); if (client == null || client.level == null || client.player == null) return out;
        List<ArmorStand> stands = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) if (entity instanceof ArmorStand stand && stand.hasCustomName() && stand.distanceTo(client.player) <= HOLOGRAM_RANGE) stands.add(stand);
        stands.sort(Comparator.comparingDouble(stand -> stand.distanceToSqr(client.player)));
        for (ArmorStand stand : stands) {
            JsonObject entry = new JsonObject(); entry.addProperty("id", stand.getId()); entry.addProperty("text", stand.getCustomName().getString());
            entry.addProperty("x", round(stand.getX())); entry.addProperty("y", round(stand.getY())); entry.addProperty("z", round(stand.getZ())); entry.addProperty("dist", round(stand.distanceTo(client.player))); entry.addProperty("marker", stand.isMarker()); out.add(entry);
        }
        return out;
    }

    private static JsonArray itemSlots(List<ItemStack> items) {
        JsonArray out = new JsonArray(); if (items == null) return out;
        for (int slot = 0; slot < items.size(); slot++) { ItemStack stack = items.get(slot); if (stack == null || stack.isEmpty()) continue;
            JsonObject item = new JsonObject(); item.addProperty("slot", slot); item.addProperty("name", stack.getHoverName().getString()); item.addProperty("count", stack.getCount()); out.add(item); }
        return out;
    }

    private static Path save(JsonObject recording) {
        try {
            Path dir = FabricRuntime.configPath().resolve("jev-poi-recordings"); Files.createDirectories(dir);
            String stem = recording.get("id").getAsString() + "-" + sanitizeFile(recording.get("label").getAsString());
            Path target = dir.resolve(stem + ".json"), temporary = dir.resolve(stem + ".tmp"); Files.writeString(temporary, GSON.toJson(recording), StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            return target;
        } catch (IOException | RuntimeException exception) { MicxFabric.LOGGER.warn("Unable to save Jev POI recording", exception); return null; }
    }

    private static String sanitizeLabel(String raw) { if (raw == null) return ""; String clean = raw.trim().replaceAll("\\s+", " "); return clean.substring(0, Math.min(48, clean.length())); }
    private static String sanitizeFile(String label) { String clean = sanitizeLabel(label).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-"); return clean.isBlank() ? "unlabelled" : clean; }
    private static double round(double value) { return Math.round(value * 1000.0) / 1000.0; }
}

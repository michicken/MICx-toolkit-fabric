package dev.micx.micxfabric.jev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.micx.micxfabric.PacketLogModule;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Set;

/**
 * Bounded, read-only feedback stream for the external Jev decision loop.
 *
 * <p>This is intentionally separate from the optional disk PacketLog and the human POI recorder:
 * Jev needs the latest server feedback even when neither recorder is enabled. High-rate movement,
 * keep-alive, and entity packets are excluded; chat, menus, item updates, and interaction requests
 * retain their original timestamp and monotonic sequence so a purchase can be verified against the
 * response that followed it.
 */
public final class JevFeedbackJournal {
    private static final JevFeedbackJournal INSTANCE = new JevFeedbackJournal();
    private static final int CAPACITY = 160;
    private static final Set<String> SEMANTIC_PACKET_NAMES = Set.of(
            "ClientboundSystemChatPacket", "ClientboundPlayerChatPacket", "ClientboundDisguisedChatPacket",
            "ClientboundSetActionBarTextPacket", "ClientboundSetTitleTextPacket",
            "ClientboundSetSubtitleTextPacket", "ClientboundOpenScreenPacket",
            "ClientboundContainerSetContentPacket", "ClientboundContainerSetSlotPacket",
            "ClientboundSetPlayerInventoryPacket", "ClientboundSetCursorItemPacket",
            "ClientboundContainerClosePacket", "ServerboundUseItemOnPacket", "ServerboundUseItemPacket",
            "ServerboundInteractPacket", "ServerboundContainerClickPacket", "ServerboundContainerClosePacket",
            "ServerboundChatPacket");

    private final Object lock = new Object();
    private final ArrayDeque<JsonObject> events = new ArrayDeque<>();
    private long sequence;
    private long startedAt;

    private JevFeedbackJournal() {
        reset();
    }

    public static JevFeedbackJournal instance() {
        return INSTANCE;
    }

    /** Called from Connection mixins, including the network thread. */
    public void capture(boolean outbound, Packet<?> packet) {
        if (packet == null || !SEMANTIC_PACKET_NAMES.contains(packet.getClass().getSimpleName())) return;
        long now = System.currentTimeMillis();
        JsonObject event = new JsonObject();
        event.addProperty("at", now);
        event.addProperty("direction", outbound ? "out" : "in");
        event.addProperty("packet", packet.getClass().getSimpleName());
        event.addProperty("kind", kind(packet));
        event.addProperty("summary", truncate(PacketLogModule.describe(packet), 240));
        addDetails(event, packet);
        synchronized (lock) {
            if (startedAt == 0L) startedAt = now;
            event.addProperty("seq", ++sequence);
            event.addProperty("offset_ms", Math.max(0L, now - startedAt));
            events.addLast(event);
            while (events.size() > CAPACITY) events.removeFirst();
        }
    }

    /** Drop stale events when the client connection is closed or a new world is entered. */
    public void reset() {
        synchronized (lock) {
            events.clear();
            sequence = 0L;
            startedAt = System.currentTimeMillis();
        }
    }

    public JsonObject snapshot() {
        JsonObject out = new JsonObject();
        JsonArray all = new JsonArray();
        JsonArray chat = new JsonArray();
        synchronized (lock) {
            out.addProperty("schema", "jev-feedback-v1");
            out.addProperty("started_at", startedAt);
            out.addProperty("latest_seq", sequence);
            out.addProperty("capacity", CAPACITY);
            for (JsonObject event : events) {
                all.add(event);
                String kind = event.has("kind") ? event.get("kind").getAsString() : "";
                if (kind.startsWith("chat")) chat.add(event);
            }
        }
        out.add("events", all);
        out.add("chat", chat);
        return out;
    }

    public long latestSequence() {
        synchronized (lock) {
            return sequence;
        }
    }

    private static String kind(Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundDisguisedChatPacket || packet instanceof ServerboundChatPacket) {
            return "chat";
        }
        if (packet instanceof ClientboundSetActionBarTextPacket) return "chat_actionbar";
        if (packet instanceof ClientboundSetTitleTextPacket) return "chat_title";
        if (packet instanceof ClientboundSetSubtitleTextPacket) return "chat_subtitle";
        if (packet instanceof ClientboundOpenScreenPacket) return "screen_open";
        if (packet instanceof ClientboundContainerSetContentPacket) return "container_content";
        if (packet instanceof ClientboundContainerSetSlotPacket
                || packet instanceof ClientboundSetPlayerInventoryPacket
                || packet instanceof ClientboundSetCursorItemPacket) return "inventory_update";
        if (packet instanceof ClientboundContainerClosePacket || packet instanceof ServerboundContainerClosePacket) {
            return "screen_close";
        }
        if (packet instanceof ServerboundContainerClickPacket) return "container_click";
        if (packet instanceof ServerboundUseItemOnPacket) return "use_item_on";
        if (packet instanceof ServerboundUseItemPacket) return "use_item";
        if (packet instanceof ServerboundInteractPacket) return "interact";
        return "semantic_packet";
    }

    private static void addDetails(JsonObject event, Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket chat) {
            event.addProperty("channel", chat.overlay() ? "actionbar" : "chat");
            event.addProperty("text", chat.content().getString());
        } else if (packet instanceof ClientboundPlayerChatPacket chat) {
            event.addProperty("channel", "chat");
            event.addProperty("sender", String.valueOf(chat.sender()));
            event.addProperty("text", chat.unsignedContent() == null
                    ? chat.body().content() : chat.unsignedContent().getString());
        } else if (packet instanceof ClientboundDisguisedChatPacket chat) {
            event.addProperty("channel", "chat");
            event.addProperty("text", chat.message().getString());
        } else if (packet instanceof ServerboundChatPacket chat) {
            event.addProperty("channel", "chat_out");
            event.addProperty("text", chat.message());
        } else if (packet instanceof ClientboundSetActionBarTextPacket actionBar) {
            event.addProperty("channel", "actionbar");
            event.addProperty("text", actionBar.text().getString());
        } else if (packet instanceof ClientboundSetTitleTextPacket title) {
            event.addProperty("channel", "title");
            event.addProperty("text", title.text().getString());
        } else if (packet instanceof ClientboundSetSubtitleTextPacket subtitle) {
            event.addProperty("channel", "subtitle");
            event.addProperty("text", subtitle.text().getString());
        } else if (packet instanceof ClientboundOpenScreenPacket open) {
            event.addProperty("container_id", open.getContainerId());
            event.addProperty("container_type", String.valueOf(open.getType()));
            event.addProperty("title", open.getTitle().getString());
        } else if (packet instanceof ClientboundContainerSetContentPacket content) {
            event.addProperty("container_id", content.containerId());
            event.add("slots", itemSlots(content.items()));
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            event.addProperty("container_id", slot.getContainerId());
            event.addProperty("slot", slot.getSlot());
            event.add("item", itemJson(slot.getItem()));
        } else if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
            event.addProperty("slot", inventory.slot());
            event.add("item", itemJson(inventory.contents()));
        } else if (packet instanceof ClientboundSetCursorItemPacket cursor) {
            event.add("item", itemJson(cursor.contents()));
        } else if (packet instanceof ClientboundContainerClosePacket close) {
            event.addProperty("container_id", close.getContainerId());
        } else if (packet instanceof ServerboundContainerClosePacket close) {
            event.addProperty("container_id", close.getContainerId());
        } else if (packet instanceof ServerboundContainerClickPacket click) {
            event.addProperty("container_id", click.containerId());
            event.addProperty("state_id", click.stateId());
            event.addProperty("slot", click.slotNum());
            event.addProperty("button", click.buttonNum());
            event.addProperty("input", String.valueOf(click.containerInput()));
        } else if (packet instanceof ServerboundUseItemOnPacket use) {
            event.addProperty("hand", String.valueOf(use.getHand()));
            if (use.getHitResult() != null) {
                event.addProperty("block", use.getHitResult().getBlockPos().toShortString());
                event.addProperty("face", use.getHitResult().getDirection().getName());
            }
        } else if (packet instanceof ServerboundUseItemPacket use) {
            event.addProperty("hand", String.valueOf(use.getHand()));
        } else if (packet instanceof ServerboundInteractPacket interact) {
            event.addProperty("entity_id", interact.entityId());
            event.addProperty("hand", String.valueOf(interact.hand()));
            event.addProperty("secondary", interact.usingSecondaryAction());
        }
    }

    private static JsonArray itemSlots(java.util.List<ItemStack> items) {
        JsonArray out = new JsonArray();
        if (items == null) return out;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack == null || stack.isEmpty()) continue;
            JsonObject item = itemJson(stack);
            item.addProperty("slot", slot);
            out.add(item);
        }
        return out;
    }

    private static JsonObject itemJson(ItemStack stack) {
        JsonObject out = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            out.addProperty("empty", true);
            return out;
        }
        out.addProperty("name", stack.getHoverName().getString());
        out.addProperty("count", stack.getCount());
        return out;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}

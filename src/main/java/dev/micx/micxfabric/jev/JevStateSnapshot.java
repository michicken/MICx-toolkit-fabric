package dev.micx.micxfabric.jev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.micx.micxfabric.AimbotModule;
import dev.micx.micxfabric.RemoteShopModule;
import dev.micx.micxfabric.ReviveAuraModule;
import dev.micx.micxfabric.WindowSpawnCounterModule;
import dev.micx.micxfabric.ZombiesPowerUpTracker;
import dev.micx.micxfabric.ZombiesTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 给外部 Jev 代理看的状态快照（每个客户端 tick 重建一次）。
 *
 * <p>设计口径：模组只负责「如实报数」，不做判断。特别是弹药——主手物品的 lore 原文直接进 JSON，
 * 让 Python 侧正则去提，改口径不用重编模组。
 *
 * <p>每个 section 单独 try/catch：任何一块炸了只往 {@code errors} 里记一行，快照整体照常产出。
 * 本方法只在客户端线程被调用（JevBridgeModule.tick）。
 */
public final class JevStateSnapshot {
    /** The network-loaded entity radius Jev may reason about.  It is not server omniscience. */
    private static final double ENTITY_RANGE = 48.0;
    private static final int HOSTILE_LIMIT = 64;
    private static final int OTHER_LIMIT = 16;
    private static final int ITEM_LIMIT = 16;
    private static final int PROJECTILE_LIMIT = 16;

    private JevStateSnapshot() {
    }

    public static JsonObject build(Minecraft client) {
        JsonObject root = new JsonObject();
        root.addProperty("t", System.currentTimeMillis());
        if (client == null) {
            root.addProperty("ok", false);
            root.addProperty("reason", "client-null");
            return root;
        }
        JsonArray errors = new JsonArray();
        LocalPlayer me = client.player;
        guard(errors, "me", () -> root.add("me", meJson(me)));
        guard(errors, "inventory", () -> inventoryInto(root, me));
        guard(errors, "game", () -> gameInto(root));
        guard(errors, "entities", () -> entitiesInto(root, client, me, errors));
        guard(errors, "windows", () -> windowsInto(root, me, errors));
        guard(errors, "powerups", () -> powerupsInto(root, me));
        guard(errors, "shops", () -> shopsInto(root, client));
        guard(errors, "modules", () -> root.add("modules", modulesJson()));
        guard(errors, "baritone", () -> root.add("baritone", baritoneJson()));
        guard(errors, "movefix", () -> root.addProperty("movefix", JevMoveFix.status()));
        guard(errors, "client", () -> {
            root.addProperty("paused", client.isPaused());
            root.addProperty("has_screen", client.gui != null && client.gui.screen() != null);
        });
        root.add("errors", errors);
        root.addProperty("ok", true);
        return root;
    }

    private static void guard(JsonArray errors, String section, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            errors.add(section + ": " + t);
        }
    }

    private static JsonObject meJson(LocalPlayer me) {
        JsonObject o = new JsonObject();
        if (me == null) {
            o.addProperty("present", false);
            return o;
        }
        o.addProperty("present", true);
        o.addProperty("name", me.getName().getString());
        o.addProperty("x", round(me.getX()));
        o.addProperty("y", round(me.getY()));
        o.addProperty("z", round(me.getZ()));
        o.addProperty("yaw", round(me.getYRot()));
        o.addProperty("pitch", round(me.getXRot()));
        o.addProperty("hp", round(me.getHealth()));
        o.addProperty("max_hp", round(me.getMaxHealth()));
        o.addProperty("absorb", round(me.getAbsorptionAmount()));
        // 弹药真源：Hypixel Zombies 把「当前手持枪的剩余弹药」画在经验条上，
        // 切枪后约 0.5s 更新；经验等级=1 表示这把枪完全没弹（用户口径 2026-09-21）。
        o.addProperty("xp_level", me.experienceLevel);
        o.addProperty("xp_progress", round(me.experienceProgress));
        o.addProperty("xp_total", me.totalExperience);
        o.addProperty("alive", me.isAlive());
        o.addProperty("down", isDown(me));
        return o;
    }

    private static void inventoryInto(JsonObject root, LocalPlayer me) {
        if (me == null) return;
        JsonArray hotbar = new JsonArray();
        for (int slot = 0; slot < 9; slot++) {
            hotbar.add(itemJson(me.getInventory().getItem(slot), slot));
        }
        root.add("hotbar", hotbar);
        int selected = me.getInventory().getSelectedSlot();
        root.addProperty("selected_slot", selected);
        root.add("main_hand", itemJson(me.getMainHandItem(), selected));
    }

    private static JsonObject itemJson(ItemStack stack, int slot) {
        JsonObject o = new JsonObject();
        o.addProperty("slot", slot);
        if (stack == null || stack.isEmpty()) {
            o.addProperty("empty", true);
            return o;
        }
        try {
            o.addProperty("name", stack.getHoverName().getString());
            o.addProperty("count", stack.getCount());
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                JsonArray lines = new JsonArray();
                for (Component line : lore.lines()) {
                    lines.add(line.getString());
                }
                o.add("lore", lines);
            }
        } catch (Throwable t) {
            o.addProperty("error", String.valueOf(t));
        }
        return o;
    }

    private static void gameInto(JsonObject root) {
        ZombiesTracker tracker = ZombiesTracker.instance();
        long now = System.currentTimeMillis();
        root.addProperty("in_game", tracker.isInZombies());
        root.addProperty("round", tracker.round());
        root.addProperty("zombies_left", tracker.zombiesLeft());
        root.addProperty("game_time_s", tracker.gameTimeSeconds());
        root.addProperty("gold", tracker.selfGold());
        String sidebar = tracker.sidebarTitle();
        root.addProperty("sidebar", sidebar == null ? "" : sidebar);
        JsonObject events = new JsonObject();
        try {
            var state = tracker.eventState();
            events.addProperty("total_downs", state.totalDowns());
            events.addProperty("total_deaths", state.totalDeaths());
            events.addProperty("total_revives", state.totalRevives());
            events.addProperty("game_over_pending", state.hasGameOverPending());
            events.addProperty("revive_protection_ms", state.reviveProtectionRemainingMs(now));
        } catch (Throwable t) {
            events.addProperty("error", String.valueOf(t));
        }
        root.add("events", events);
    }

    private static void entitiesInto(JsonObject root, Minecraft client, LocalPlayer me, JsonArray errors) {
        JsonArray mates = new JsonArray();
        JsonArray zombies = new JsonArray();
        JsonArray others = new JsonArray();
        JsonArray items = new JsonArray();
        JsonArray projectiles = new JsonArray();
        root.add("teammates", mates);
        root.add("zombies", zombies);
        JsonObject perception = new JsonObject();
        perception.addProperty("schema", "loaded-entity-v1");
        perception.addProperty("range", ENTITY_RANGE);
        perception.addProperty("boundary", "Only entities loaded by this client are known; unloaded or server-hidden entities are unknown.");
        perception.add("other_living", others);
        perception.add("items", items);
        perception.add("projectiles", projectiles);
        root.add("perception", perception);
        if (client.level == null) return;

        Map<String, String> statuses = Map.of();
        Map<String, Long> downSince = Map.of();
        try {
            var state = ZombiesTracker.instance().eventState();
            statuses = state.statuses();
            downSince = state.downSince();
        } catch (Throwable t) {
            errors.add("status-maps: " + t);
        }
        long now = System.currentTimeMillis();
        List<LivingEntity> hostiles = new ArrayList<>();
        List<LivingEntity> otherLiving = new ArrayList<>();
        List<ItemEntity> nearbyItems = new ArrayList<>();
        List<Projectile> nearbyProjectiles = new ArrayList<>();
        int scanned = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == null) continue;
            scanned++;
            if (entity instanceof Player other) {
                if (me == null || other == me) continue;
                mates.add(mateJson(other, me, statuses, downSince, now));
                continue;
            }
            if (me == null || entity.distanceTo(me) > ENTITY_RANGE) continue;
            if (entity instanceof WitherBoss) continue; // User policy: never expose withers to Jev.
            if (entity instanceof LivingEntity living && living.isAlive()) {
                if (isHostile(living)) hostiles.add(living);
                else otherLiving.add(living);
            } else if (entity instanceof ItemEntity item) {
                nearbyItems.add(item);
            } else if (entity instanceof Projectile projectile) {
                nearbyProjectiles.add(projectile);
            }
        }
        perception.addProperty("loaded_entity_count", scanned);
        perception.addProperty("hostile_total", hostiles.size());
        perception.addProperty("other_living_total", otherLiving.size());
        perception.addProperty("item_total", nearbyItems.size());
        perception.addProperty("projectile_total", nearbyProjectiles.size());
        if (me != null) {
            hostiles.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(me)));
            otherLiving.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(me)));
            nearbyItems.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(me)));
            nearbyProjectiles.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(me)));
            perception.addProperty("hostiles_truncated", hostiles.size() > HOSTILE_LIMIT);
            perception.addProperty("other_living_truncated", otherLiving.size() > OTHER_LIMIT);
            perception.addProperty("items_truncated", nearbyItems.size() > ITEM_LIMIT);
            perception.addProperty("projectiles_truncated", nearbyProjectiles.size() > PROJECTILE_LIMIT);
            perception.add("pressure", pressureJson(hostiles, me));
            for (int i = 0; i < Math.min(HOSTILE_LIMIT, hostiles.size()); i++) {
                LivingEntity mob = hostiles.get(i);
                zombies.add(mobJson(mob, me));
            }
            for (int i = 0; i < Math.min(OTHER_LIMIT, otherLiving.size()); i++) {
                others.add(entityJson(otherLiving.get(i), me));
            }
            for (int i = 0; i < Math.min(ITEM_LIMIT, nearbyItems.size()); i++) {
                items.add(entityJson(nearbyItems.get(i), me));
            }
            for (int i = 0; i < Math.min(PROJECTILE_LIMIT, nearbyProjectiles.size()); i++) {
                projectiles.add(entityJson(nearbyProjectiles.get(i), me));
            }
        }
    }

    private static JsonObject mateJson(Player other, LocalPlayer me,
                                       Map<String, String> statuses, Map<String, Long> downSince, long now) {
        JsonObject o = new JsonObject();
        String name = other.getName().getString();
        o.addProperty("name", name);
        o.addProperty("hp", round(other.getHealth()));
        o.addProperty("down", isDown(other));
        String status = statuses.get(name);
        if (status != null) o.addProperty("status", status);
        Long since = downSince.get(name);
        if (since != null) o.addProperty("down_ms", Math.max(0L, now - since));
        o.addProperty("x", round(other.getX()));
        o.addProperty("y", round(other.getY()));
        o.addProperty("z", round(other.getZ()));
        o.addProperty("dist", round(me.distanceTo(other)));
        return o;
    }

    private static JsonObject mobJson(Entity mob, LocalPlayer me) {
        JsonObject o = new JsonObject();
        o.addProperty("id", mob.getId());
        o.addProperty("name", mob.getName().getString());
        o.addProperty("cls", mob.getClass().getSimpleName());
        o.addProperty("x", round(mob.getX()));
        o.addProperty("y", round(mob.getY()));
        o.addProperty("z", round(mob.getZ()));
        o.addProperty("dist", round(me.distanceTo(mob)));
        o.addProperty("type", typeKey(mob));
        o.addProperty("bearing", round(bearing(me, mob)));
        o.addProperty("relative_bearing", round(relativeBearing(me, mob)));
        o.addProperty("line_of_sight", me.hasLineOfSight(mob));
        Vec3 velocity = mob.getDeltaMovement();
        o.addProperty("vx", round(velocity.x * 20.0));
        o.addProperty("vy", round(velocity.y * 20.0));
        o.addProperty("vz", round(velocity.z * 20.0));
        o.addProperty("speed", round(Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0));
        o.addProperty("closing_speed", round(closingSpeed(mob, me)));
        if (mob instanceof LivingEntity living) {
            o.addProperty("hp", round(living.getHealth()));
            o.addProperty("max_hp", round(living.getMaxHealth()));
            o.addProperty("absorb", round(living.getAbsorptionAmount()));
            o.addProperty("baby", living.isBaby());
        }
        try {
            WindowSpawnCounterModule counter = WindowSpawnCounterModule.instance();
            String window = counter.birthWindowIdOf(mob.getId());
            if (window != null) o.addProperty("window", window);
            long birth = counter.birthMsOf(mob.getId());
            if (birth > 0L) o.addProperty("birth_ms", Math.max(0L, System.currentTimeMillis() - birth));
            String golem = counter.golemTagOf(mob.getId());
            if (golem != null) o.addProperty("golem", golem);
        } catch (Throwable ignored) {
        }
        return o;
    }

    private static JsonObject entityJson(Entity entity, LocalPlayer me) {
        JsonObject o = new JsonObject();
        o.addProperty("id", entity.getId());
        o.addProperty("name", entity.getName().getString());
        o.addProperty("type", typeKey(entity));
        o.addProperty("x", round(entity.getX()));
        o.addProperty("y", round(entity.getY()));
        o.addProperty("z", round(entity.getZ()));
        o.addProperty("dist", round(me.distanceTo(entity)));
        o.addProperty("bearing", round(bearing(me, entity)));
        o.addProperty("relative_bearing", round(relativeBearing(me, entity)));
        if (entity instanceof LivingEntity living) {
            o.addProperty("hp", round(living.getHealth()));
            o.addProperty("max_hp", round(living.getMaxHealth()));
        }
        return o;
    }

    private static JsonObject pressureJson(List<LivingEntity> hostiles, LocalPlayer me) {
        int within3 = 0;
        int within6 = 0;
        int within10 = 0;
        int closing = 0;
        for (LivingEntity hostile : hostiles) {
            double distance = hostile.distanceTo(me);
            if (distance <= 3.0) within3++;
            if (distance <= 6.0) within6++;
            if (distance <= 10.0) within10++;
            if (closingSpeed(hostile, me) > 0.5) closing++;
        }
        JsonObject o = new JsonObject();
        o.addProperty("within_3", within3);
        o.addProperty("within_6", within6);
        o.addProperty("within_10", within10);
        o.addProperty("closing", closing);
        return o;
    }

    private static String typeKey(Entity entity) {
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /** Same target class as combat modules: monsters plus Hypixel's golem/wolf enemy skins. */
    private static boolean isHostile(LivingEntity living) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getPath();
        return !(living instanceof WitherBoss)
                && (living instanceof Monster || "iron_golem".equals(path) || "wolf".equals(path));
    }

    /** Minecraft yaw: 0 faces +Z, so atan2(-dx, dz) is in the same convention. */
    private static double bearing(LocalPlayer me, Entity entity) {
        return wrapDegrees(Math.toDegrees(Math.atan2(-(entity.getX() - me.getX()), entity.getZ() - me.getZ())));
    }

    private static double relativeBearing(LocalPlayer me, Entity entity) {
        return wrapDegrees(bearing(me, entity) - me.getYRot());
    }

    /** Positive means the entity's current horizontal velocity is reducing distance to the player. */
    private static double closingSpeed(Entity entity, LocalPlayer me) {
        double dx = me.getX() - entity.getX();
        double dz = me.getZ() - entity.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001) return 0.0;
        Vec3 velocity = entity.getDeltaMovement();
        return ((velocity.x * dx + velocity.z * dz) / distance) * 20.0;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private static void windowsInto(JsonObject root, LocalPlayer me, JsonArray errors) {
        JsonArray out = new JsonArray();
        root.add("windows", out);
        WindowSpawnCounterModule counter = WindowSpawnCounterModule.instance();
        int[] counts = counter.snapshot();
        int[] totals = counter.totalSnapshot();
        List<WindowSpawnCounterModule.WindowDef> defs = WindowSpawnCounterModule.WINDOWS;
        for (int i = 0; i < defs.size(); i++) {
            WindowSpawnCounterModule.WindowDef def = defs.get(i);
            JsonObject w = new JsonObject();
            w.addProperty("id", def.id);
            // def is the server's raw spawn marker, not a walkable destination.  Keep it
            // explicitly separate so external agents cannot accidentally path into a window.
            JsonObject spawn = new JsonObject();
            spawn.addProperty("x", round(def.x));
            spawn.addProperty("y", round(def.y));
            spawn.addProperty("z", round(def.z));
            w.add("spawn", spawn);
            JsonObject edge = JevWindowAnchors.edgeFor(def.id);
            if (edge != null) {
                w.add("edge", edge);
                // Compatibility fields now mean the walkable edge, never the raw spawn marker.
                w.addProperty("x", edge.get("x").getAsDouble());
                w.addProperty("y", edge.get("y").getAsDouble());
                w.addProperty("z", edge.get("z").getAsDouble());
            }
            w.addProperty("edge_available", edge != null);
            if (i < counts.length) w.addProperty("count", counts[i]);
            if (i < totals.length) w.addProperty("total", totals[i]);
            if (me != null) {
                double wx = edge == null ? def.x : edge.get("x").getAsDouble();
                double wz = edge == null ? def.z : edge.get("z").getAsDouble();
                double dx = wx - me.getX();
                double dz = wz - me.getZ();
                w.addProperty("dist", round(Math.sqrt(dx * dx + dz * dz)));
            }
            out.add(w);
        }
        try {
            root.addProperty("too_alert", counter.tooWindowAlert());
            String lastToo = counter.lastTooWindowId();
            root.addProperty("last_too_window", lastToo == null ? "" : lastToo);
        } catch (Throwable t) {
            errors.add("too-alert: " + t);
        }
    }

    private static void powerupsInto(JsonObject root, LocalPlayer me) {
        JsonArray out = new JsonArray();
        root.add("powerups", out);
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, ZombiesPowerUpTracker.DropVisual> entry
                : ZombiesPowerUpTracker.instance().dropsSnapshot().entrySet()) {
            ZombiesPowerUpTracker.DropVisual drop = entry.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("id", drop.entityId());
            o.addProperty("kind", drop.kind());
            o.addProperty("round", drop.round());
            o.addProperty("x", round(drop.x()));
            o.addProperty("y", round(drop.y()));
            o.addProperty("z", round(drop.z()));
            if (drop.expiresAt() > 0L) {
                o.addProperty("remain_ms", Math.max(0L, drop.expiresAt() - now));
            }
            if (me != null) {
                double dx = drop.x() - me.getX();
                double dy = drop.y() - me.getY();
                double dz = drop.z() - me.getZ();
                o.addProperty("dist", round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
            }
            out.add(o);
        }
    }

    private static void shopsInto(JsonObject root, Minecraft client) {
        JsonArray out = new JsonArray();
        root.add("shops", out);
        RemoteShopModule shop = RemoteShopModule.instance();
        for (RemoteShopModule.Candidate candidate : shop.scan(client, false)) {
            JsonObject o = new JsonObject();
            o.addProperty("id", candidate.entityId());
            o.addProperty("name", candidate.name());
            o.addProperty("kind", candidate.kind());
            o.addProperty("dist", round(candidate.distance()));
            o.addProperty("matched", candidate.matched());
            o.addProperty("x", round(candidate.position().x));
            o.addProperty("y", round(candidate.position().y));
            o.addProperty("z", round(candidate.position().z));
            out.add(o);
        }
        String report = shop.lastReport();
        root.addProperty("shop_report", report == null ? "" : report);
    }

    private static JsonObject modulesJson() {
        JsonObject out = new JsonObject();
        try {
            AimbotModule aimbot = AimbotModule.instance();
            JsonObject aim = new JsonObject();
            aim.addProperty("on", aimbot.enabled());
            aim.addProperty("brute", aimbot.config().bruteMode);
            aim.addProperty("sweep", aimbot.config().bruteSweep);
            aim.addProperty("only_fire", aimbot.config().onlyFire);
            aim.addProperty("zombies_only", aimbot.config().zombiesOnly);
            out.add("aimbot", aim);
        } catch (Throwable t) {
            out.addProperty("aimbot_error", String.valueOf(t));
        }
        try {
            ReviveAuraModule revive = ReviveAuraModule.instance();
            JsonObject aura = new JsonObject();
            aura.addProperty("on", revive.enabled());
            aura.addProperty("range", round(revive.getRange()));
            aura.addProperty("interval_ms", round(revive.getIntervalMs()));
            out.add("revive_aura", aura);
        } catch (Throwable t) {
            out.addProperty("revive_aura_error", String.valueOf(t));
        }
        try {
            out.addProperty("remote_shop_on", RemoteShopModule.instance().enabled());
        } catch (Throwable ignored) {
        }
        try {
            HeadlessModule headless = HeadlessModule.instance();
            JsonObject h = new JsonObject();
            h.addProperty("on", headless.enabled());
            h.addProperty("hidden", HeadlessModule.windowHidden());
            h.addProperty("skip_render", HeadlessModule.skipRenderActive());
            h.addProperty("rotation_on_tick", HeadlessModule.rotationOnTickActive());
            out.add("headless", h);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static JsonObject baritoneJson() {
        JsonObject out = new JsonObject();
        boolean present = BaritoneSupport.present();
        out.addProperty("present", present);
        if (!present) return out;
        try {
            out.addProperty("version", BaritoneSupport.version());
            out.addProperty("pathing", BaritoneBridge.pathing());
            out.addProperty("goal", BaritoneBridge.goalString());
            out.addProperty("command_seq", BaritoneBridge.commandSequence());
            long commandAt = BaritoneBridge.commandAtMs();
            if (commandAt > 0L) {
                out.addProperty("command_age_ms", Math.max(0L, System.currentTimeMillis() - commandAt));
                JsonObject target = new JsonObject();
                target.addProperty("x", round(BaritoneBridge.targetX()));
                target.addProperty("y", round(BaritoneBridge.targetY()));
                target.addProperty("z", round(BaritoneBridge.targetZ()));
                target.addProperty("range", BaritoneBridge.targetRange());
                out.add("target", target);
            }
            String error = BaritoneBridge.lastError();
            if (error != null && !error.isEmpty()) out.addProperty("last_error", error);
        } catch (Throwable t) {
            out.addProperty("error", String.valueOf(t));
        }
        return out;
    }

    private static boolean isDown(Player player) {
        try {
            return player.isSleeping() || player.getPose() == Pose.SLEEPING;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double round(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }
}

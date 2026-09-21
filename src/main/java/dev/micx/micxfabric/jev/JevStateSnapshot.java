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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

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
    private static final int MOB_LIMIT = 20;
    private static final double MOB_RANGE = 40.0;

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
        root.add("teammates", mates);
        root.add("zombies", zombies);
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
        List<Entity> mobs = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == null) continue;
            if (entity instanceof Player other) {
                if (me == null || other == me) continue;
                mates.add(mateJson(other, me, statuses, downSince, now));
            } else if (me != null
                    && entity instanceof LivingEntity living
                    && living.isAlive()
                    && living.distanceTo(me) <= MOB_RANGE) {
                mobs.add(entity);
            }
        }
        if (me != null) {
            mobs.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(me)));
            int taken = 0;
            for (Entity mob : mobs) {
                if (taken++ >= MOB_LIMIT) break;
                zombies.add(mobJson(mob, me));
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
            w.addProperty("x", round(def.x));
            w.addProperty("y", round(def.y));
            w.addProperty("z", round(def.z));
            if (i < counts.length) w.addProperty("count", counts[i]);
            if (i < totals.length) w.addProperty("total", totals[i]);
            if (me != null) {
                double dx = def.x - me.getX();
                double dz = def.z - me.getZ();
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

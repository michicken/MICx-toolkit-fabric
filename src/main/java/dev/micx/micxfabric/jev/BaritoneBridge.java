package dev.micx.micxfabric.jev;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * Baritone 寻路的唯一入口（走位交给 Baritone，瞄准留在 Aimbot）。
 *
 * <p>僵尸末日不是挖矿场景：这里把 Baritone 的挖掘/放置/自由视角外的默认值收敛成「只走路」，
 * 免得它在局内挖墙拆图，或被 Watchdog 当成异常行为。所有调用都吞异常只回字符串，
 * 桥接线程永远不会因为 Baritone 内部状态炸掉。
 *
 * <p>只在 {@link BaritoneSupport#present()} 为真时被调用（见 JevCommands）。
 */
public final class BaritoneBridge {
    private static boolean settingsApplied;
    private static String lastError = "";
    /** Last command as observed by the external decision agent.  This is telemetry only. */
    private static volatile long commandSequence;
    private static volatile long commandAtMs;
    private static volatile double targetX;
    private static volatile double targetY;
    private static volatile double targetZ;
    private static volatile int targetRange;

    private BaritoneBridge() {
    }

    private static IBaritone primary() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            return null;
        }
    }

    /** 一次性收敛设置：只走不挖、不放置、允许疾跑；freeLook 保证 Aimbot 仍能自由转视角。 */
    public static void applyZombiesSettings() {
        if (settingsApplied) return;
        try {
            Settings s = BaritoneAPI.getSettings();
            s.allowBreak.value = false;
            s.allowPlace.value = false;
            s.allowSprint.value = true;
            s.freeLook.value = true;
            s.assumeSafeWalk.value = true;
            s.chatDebug.value = false;
            s.chatControl.value = false;
            settingsApplied = true;
        } catch (Throwable t) {
            lastError = String.valueOf(t);
        }
    }

    public static String gotoNear(double x, double y, double z, int range) {
        IBaritone baritone = primary();
        if (baritone == null) return "baritone-not-ready";
        try {
            applyZombiesSettings();
            int r = Math.max(1, Math.min(8, range));
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(blockPos(x, y, z), r));
            rememberTarget(x, y, z, r);
            return "ok";
        } catch (Throwable t) {
            return fail(t);
        }
    }

    public static String gotoBlock(double x, double y, double z) {
        IBaritone baritone = primary();
        if (baritone == null) return "baritone-not-ready";
        try {
            applyZombiesSettings();
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(blockPos(x, y, z)));
            rememberTarget(x, y, z, 0);
            return "ok";
        } catch (Throwable t) {
            return fail(t);
        }
    }

    public static String follow(String playerName) {
        if (playerName == null || playerName.isBlank()) return "error: empty name";
        IBaritone baritone = primary();
        if (baritone == null) return "baritone-not-ready";
        try {
            applyZombiesSettings();
            baritone.getFollowProcess().follow(entity ->
                    entity instanceof Player player
                            && player.getName().getString().equalsIgnoreCase(playerName));
            return "ok";
        } catch (Throwable t) {
            return fail(t);
        }
    }

    public static String stop() {
        IBaritone baritone = primary();
        if (baritone == null) return "baritone-not-ready";
        try {
            baritone.getFollowProcess().cancel();
            baritone.getPathingBehavior().cancelEverything();
            commandSequence++;
            commandAtMs = System.currentTimeMillis();
            return "ok";
        } catch (Throwable t) {
            return fail(t);
        }
    }

    public static boolean pathing() {
        IBaritone baritone = primary();
        if (baritone == null) return false;
        try {
            return baritone.getPathingBehavior().isPathing();
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            return false;
        }
    }

    public static String goalString() {
        IBaritone baritone = primary();
        if (baritone == null) return "baritone-not-ready";
        try {
            Object goal = baritone.getPathingBehavior().getGoal();
            return goal == null ? "" : goal.toString();
        } catch (Throwable t) {
            return "error: " + t;
        }
    }

    public static String lastError() {
        return lastError;
    }

    public static long commandSequence() {
        return commandSequence;
    }

    public static long commandAtMs() {
        return commandAtMs;
    }

    public static double targetX() {
        return targetX;
    }

    public static double targetY() {
        return targetY;
    }

    public static double targetZ() {
        return targetZ;
    }

    public static int targetRange() {
        return targetRange;
    }

    private static void rememberTarget(double x, double y, double z, int range) {
        targetX = x;
        targetY = y;
        targetZ = z;
        targetRange = range;
        commandAtMs = System.currentTimeMillis();
        commandSequence++;
    }

    private static BlockPos blockPos(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static String fail(Throwable t) {
        lastError = String.valueOf(t);
        return "error: " + t;
    }
}

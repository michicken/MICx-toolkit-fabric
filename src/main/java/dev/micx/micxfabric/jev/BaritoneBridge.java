package dev.micx.micxfabric.jev;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.event.events.SprintStateEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.event.listener.IEventBus;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

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
    private static IEventBus sprintGuardBus;

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

    /** Start a bounded walk to a Jev anchor. Movement legality is enforced by JevMoveFix. */
    public static String gotoNear(double x, double y, double z, int range) {
        IBaritone baritone = primary();
        if (baritone == null) return "baritone-not-ready";
        try {
            applyZombiesSettings();
            installSprintGuard(baritone);
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
            installSprintGuard(baritone);
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
            installSprintGuard(baritone);
            baritone.getFollowProcess().follow(entity ->
                    entity instanceof Player player
                            && player.getName().getString().equalsIgnoreCase(playerName));
            return "ok";
        } catch (Throwable t) {
            return fail(t);
        }
    }

    public static String stop() {
        JevMoveFix.clearPathCorrection();
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

    /**
     * Returns the next horizontal direction of Baritone's active path in world coordinates.
     * The result is deliberately a direction, not a key state: JevMoveFix converts it through
     * the player's actual camera yaw before Minecraft builds the input packet.
     */
    public static Vec3 currentPathDirection() {
        IBaritone baritone = primary();
        if (baritone == null) return null;
        try {
            if (!baritone.getPathingBehavior().isPathing()) return null;
            LocalPathPoint point = nextPathPoint(baritone);
            if (point != null) return new Vec3(point.x - playerX(baritone), 0.0, point.z - playerZ(baritone));
            if (commandAtMs > 0L) {
                return new Vec3(targetX - playerX(baritone), 0.0, targetZ - playerZ(baritone));
            }
        } catch (Throwable t) {
            lastError = String.valueOf(t);
        }
        return null;
    }

    /** Whether Baritone itself is configured to request sprint when vanilla input permits it. */
    public static boolean sprintAllowed() {
        try {
            return Boolean.TRUE.equals(BaritoneAPI.getSettings().allowSprint.value);
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

    private static void installSprintGuard(IBaritone baritone) {
        try {
            IEventBus bus = baritone.getGameEventHandler();
            if (bus == null || bus == sprintGuardBus) return;
            bus.registerEventListener(new AbstractGameEventListener() {
                @Override
                public void onPlayerSprintState(SprintStateEvent event) {
                    // Do not disable sprint globally. Only reject a request when the current
                    // path/camera frame would make it a backward, sideways, airborne, or
                    // otherwise vanilla-illegal sprint under Grim's movement prediction.
                    if (JevMoveFix.shouldBlockBaritoneSprint()) event.setState(false);
                }
            });
            sprintGuardBus = bus;
        } catch (Throwable t) {
            lastError = String.valueOf(t);
        }
    }

    private static LocalPathPoint nextPathPoint(IBaritone baritone) {
        IPathExecutor executor = baritone.getPathingBehavior().getCurrent();
        if (executor == null) return null;
        IPath path = executor.getPath();
        if (path == null) return null;
        List<BetterBlockPos> positions = path.positions();
        if (positions == null || positions.isEmpty()) return null;
        int current = Math.max(0, executor.getPosition());
        int start = Math.min(positions.size() - 1, current + 1);
        double px = playerX(baritone);
        double pz = playerZ(baritone);
        for (int i = start; i < positions.size(); i++) {
            BetterBlockPos pos = positions.get(i);
            if (pos == null) continue;
            double x = pos.getX() + 0.5;
            double z = pos.getZ() + 0.5;
            double dx = x - px;
            double dz = z - pz;
            if (dx * dx + dz * dz > 0.04) return new LocalPathPoint(x, z);
        }
        return null;
    }

    private static double playerX(IBaritone baritone) {
        return baritone.getPlayerContext().player().getX();
    }

    private static double playerZ(IBaritone baritone) {
        return baritone.getPlayerContext().player().getZ();
    }

    private record LocalPathPoint(double x, double z) {
    }

    private static BlockPos blockPos(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static String fail(Throwable t) {
        lastError = String.valueOf(t);
        return "error: " + t;
    }
}

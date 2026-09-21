package dev.micx.micxfabric.jev;

import com.mojang.blaze3d.platform.InputConstants;
import dev.micx.micxfabric.RightClickerModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Short combat movement bursts expressed through the client's real KeyMappings.
 *
 * <p>This is deliberately not silent rotation or packet simulation. The normal client creates the
 * PLAYER_INPUT and sprint action packets, so the server observes an ordinary W/A/S/D state. A
 * Baritone path is converted from world direction into the player's actual camera frame in the
 * KeyboardInput mixin. Sprint remains available when that corrected frame is forward and vanilla
 * conditions allow it; only the illegal backward/sideways profile is rejected.
 */
public final class JevMoveFix {
    private static final int MAX_BURST_MS = 700;
    private static volatile Request active;
    private static volatile boolean pathCorrectionActive;
    private static volatile boolean pathSprintLegal;

    private JevMoveFix() {
    }

    public static String request(String style, int durationMs, boolean sprint) {
        Style parsed = Style.parse(style);
        if (parsed == null) return "error: invalid combat move";
        int duration = Math.max(50, Math.min(MAX_BURST_MS, durationMs));
        active = new Request(parsed, duration, sprint, System.currentTimeMillis());
        return "ok";
    }

    public static String clear(Minecraft client) {
        active = null;
        release(client);
        return "ok";
    }

    /**
     * Rewrites Baritone's current path direction into real client input coordinates. This runs
     * from KeyboardInput.tick, after Baritone's key overrides and immediately before LocalPlayer
     * applies input, so the movement packet and the rendered camera share the same frame.
     */
    public static Vec2 correctBaritoneInput(KeyboardInput input) {
        pathCorrectionActive = false;
        pathSprintLegal = false;
        if (input == null || active != null) return null;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.isPaused()
                || (client.gui != null && client.gui.screen() != null)
                || !JevBridgeModule.instance().enabled()
                || !BaritoneSupport.present()) return null;

        Vec3 worldDirection = BaritoneBridge.currentPathDirection();
        if (worldDirection == null) return null;
        double length = Math.sqrt(worldDirection.x * worldDirection.x + worldDirection.z * worldDirection.z);
        if (!Double.isFinite(length) || length < 0.01) return null;

        LocalPlayer player = client.player;
        Input original = input.keyPresses;
        if (original == null) return null;
        double dx = worldDirection.x / length;
        double dz = worldDirection.z / length;
        double yaw = Math.toRadians(player.getYRot());
        // Minecraft yaw 0 faces +Z. Positive local strafe is left, matching Input.left().
        double forward = dx * Math.sin(yaw) + dz * Math.cos(yaw);
        double left = dx * Math.cos(yaw) - dz * Math.sin(yaw);
        Direction direction = Direction.from(forward, left);
        // The packet carries boolean W/A/S/D states. A diagonal W+A state resolves to about
        // 0.707 forward in the server yaw frame, below Grim's roughly 0.8 guard; keep sprint for
        // a pure forward input only. It is still dynamic (camera/path aligned), not globally off.
        boolean sprint = original.sprint()
                && direction.forward
                && !direction.left
                && !direction.right
                && BaritoneBridge.sprintAllowed()
                && player.onGround()
                && !player.horizontalCollision
                && !player.isUsingItem()
                && !player.isInWater()
                && !player.isInLava()
                && !player.isPassenger()
                && !original.shift()
                && player.getFoodData().getFoodLevel() > 6;

        input.keyPresses = new Input(direction.forward, direction.backward, direction.left, direction.right,
                original.jump(), original.shift(), sprint);
        double strafe = direction.left ? 1.0 : direction.right ? -1.0 : 0.0;
        double impulse = direction.forward ? 1.0 : direction.backward ? -1.0 : 0.0;
        double magnitude = Math.sqrt(strafe * strafe + impulse * impulse);
        pathCorrectionActive = true;
        pathSprintLegal = sprint;
        return magnitude > 0.0 ? new Vec2((float) (strafe / magnitude), (float) (impulse / magnitude)) : Vec2.ZERO;
    }

    /** Called by Baritone's SprintStateEvent listener for the current corrected movement frame. */
    public static boolean shouldBlockBaritoneSprint() {
        return pathCorrectionActive && !pathSprintLegal;
    }

    public static void clearPathCorrection() {
        pathCorrectionActive = false;
        pathSprintLegal = false;
    }

    public static void tick(Minecraft client) {
        if (active == null) return;
        if (client == null || client.player == null || client.options == null || client.isPaused()
                || (client.gui != null && client.gui.screen() != null)
                || System.currentTimeMillis() - active.startedAtMs >= active.durationMs) {
            clear(client);
            return;
        }
        Style style = active.style;
        set(client, client.options.keyUp, style.forward);
        set(client, client.options.keyDown, style.backward);
        set(client, client.options.keyLeft, style.left);
        set(client, client.options.keyRight, style.right);

        boolean safeSprint = active.requestSprint && style.forward && !style.backward
                && client.player.onGround() && !client.player.horizontalCollision
                && !client.player.isUsingItem() && !client.player.isInWater();
        set(client, client.options.keySprint, safeSprint);
    }

    public static String status() {
        Request request = active;
        if (request == null) {
            return pathCorrectionActive
                    ? "baritone-corrected:" + (pathSprintLegal ? "sprint-legal" : "walk-only")
                    : "idle";
        }
        long age = Math.max(0L, System.currentTimeMillis() - request.startedAtMs);
        return request.style.name().toLowerCase() + ":" + age + "/" + request.durationMs
                + (request.requestSprint ? ":sprint-requested" : "");
    }

    private static void release(Minecraft client) {
        if (client == null || client.options == null) return;
        set(client, client.options.keyUp, false);
        set(client, client.options.keyDown, false);
        set(client, client.options.keyLeft, false);
        set(client, client.options.keyRight, false);
        set(client, client.options.keySprint, false);
    }

    private static void set(Minecraft client, KeyMapping mapping, boolean down) {
        if (mapping == null) return;
        mapping.setDown(down || physicalPressed(client, mapping));
    }

    private static boolean physicalPressed(Minecraft client, KeyMapping mapping) {
        if (client == null || client.getWindow() == null) return false;
        InputConstants.Key key = RightClickerModule.currentUseKey(mapping);
        if (key == null) return false;
        long window = client.getWindow().handle();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    private enum Style {
        HOLD(false, false, false, false),
        BACK(false, true, false, false),
        BACK_LEFT(false, true, true, false),
        BACK_RIGHT(false, true, false, true),
        LEFT(false, false, true, false),
        RIGHT(false, false, false, true),
        FORWARD(true, false, false, false),
        FORWARD_LEFT(true, false, true, false),
        FORWARD_RIGHT(true, false, false, true);

        final boolean forward, backward, left, right;

        Style(boolean forward, boolean backward, boolean left, boolean right) {
            this.forward = forward;
            this.backward = backward;
            this.left = left;
            this.right = right;
        }

        static Style parse(String value) {
            if (value == null) return null;
            try {
                return Style.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private record Direction(boolean forward, boolean backward, boolean left, boolean right) {
        static Direction from(double forward, double left) {
            double angle = Math.toDegrees(Math.atan2(left, forward));
            if (angle >= -22.5 && angle < 22.5) return new Direction(true, false, false, false);
            if (angle >= 22.5 && angle < 67.5) return new Direction(true, false, true, false);
            if (angle >= 67.5 && angle < 112.5) return new Direction(false, false, true, false);
            if (angle >= 112.5 && angle < 157.5) return new Direction(false, true, true, false);
            if (angle >= 157.5 || angle < -157.5) return new Direction(false, true, false, false);
            if (angle >= -157.5 && angle < -112.5) return new Direction(false, true, false, true);
            if (angle >= -112.5 && angle < -67.5) return new Direction(false, false, false, true);
            return new Direction(true, false, false, true);
        }
    }

    private record Request(Style style, int durationMs, boolean requestSprint, long startedAtMs) {
    }
}

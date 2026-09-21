package dev.micx.micxfabric.jev;

import com.mojang.blaze3d.platform.InputConstants;
import dev.micx.micxfabric.RightClickerModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Short combat movement bursts expressed through the client's real KeyMappings.
 *
 * <p>This is deliberately not silent rotation or packet simulation.  The normal client creates
 * the PLAYER_INPUT and sprint action packets, so the server observes an ordinary W/A/S/D state.
 * Backward and sideways fighting are walk-only; sprint is permitted only with a forward component
 * and never while colliding, using an item, or airborne.  That keeps the input within the state
 * Grim predicts instead of asking Baritone for omnidirectional backwards sprint.
 */
public final class JevMoveFix {
    private static final int MAX_BURST_MS = 700;
    private static Request active;

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
        if (request == null) return "idle";
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

    private record Request(Style style, int durationMs, boolean requestSprint, long startedAtMs) {
    }
}

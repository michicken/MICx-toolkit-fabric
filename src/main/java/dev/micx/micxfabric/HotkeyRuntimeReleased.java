package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Release-edge polling for multi-binding modules such as AutoText. */
public final class HotkeyRuntimeReleased {
    private static final Map<String, Boolean> PREVIOUS = new HashMap<>();

    private HotkeyRuntimeReleased() {
    }

    public static boolean released(String id, InputBinding binding, Minecraft client) {
        boolean blocked = client == null || client.player == null || client.level == null
                || client.gui == null || client.gui.screen() != null;
        if (blocked || binding == null || binding.isUnbound()) {
            PREVIOUS.remove(id);
            return false;
        }
        boolean down = binding.down(client);
        boolean old = PREVIOUS.getOrDefault(id, false);
        PREVIOUS.put(id, down);
        return !down && old;
    }

    /** Clears release-edge state belonging to one module. */
    public static void clearModule(String moduleId) {
        if (moduleId == null) return;
        PREVIOUS.keySet().removeIf(key -> key.equals(moduleId) || key.startsWith(moduleId + ":"));
    }

    public static void clear() {
        PREVIOUS.clear();
    }
}

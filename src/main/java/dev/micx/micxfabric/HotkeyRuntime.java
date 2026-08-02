package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Polls migrated module bindings with Forge-compatible press-edge semantics. */
public final class HotkeyRuntime {
    private static final Map<String, Boolean> PREVIOUS = new HashMap<>();

    private HotkeyRuntime() {
    }

    public static void tick(Minecraft client) {
        boolean blocked = client == null
                || client.player == null
                || client.level == null
                || client.gui == null
                || client.gui.screen() != null;
        for (Module module : ModuleRuntime.modules()) {
            handleBinding(module, module.id() + ":primary", module.primaryBinding(), blocked, client, true);
            handleBinding(module, module.id() + ":secondary", module.secondaryBinding(), blocked, client, false);
        }
    }

    private static void handleBinding(Module module, String stateId, InputBinding binding,
                                      boolean blocked, Minecraft client, boolean primary) {
        if (binding == null || binding.isUnbound()) {
            PREVIOUS.remove(stateId);
            return;
        }
        boolean down = binding.down(client);
        boolean old = PREVIOUS.getOrDefault(stateId, false);
        PREVIOUS.put(stateId, down);
        if (blocked || !down || old) return;
        boolean newlyEnabled = !module.enabled();
        if (newlyEnabled) module.setEnabled(true);
        if (!module.enabled()) return;
        if (primary) module.onPrimaryPressed(client, newlyEnabled);
        else module.onSecondaryPressed(client);
    }

    /** Clears both primary and secondary edge state for one module. */
    public static void clearModule(String moduleId) {
        if (moduleId == null) return;
        PREVIOUS.remove(moduleId);
        PREVIOUS.remove(moduleId + ":primary");
        PREVIOUS.remove(moduleId + ":secondary");
    }

    /** Backward-compatible alias used by older callers. */
    public static void clear(String id) {
        clearModule(id);
    }

    public static void clearAll() {
        PREVIOUS.clear();
    }
}

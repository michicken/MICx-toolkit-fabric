package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Edge detector shared by modules; it never polls while a Screen is open. */
public final class KeyEdgeTracker {
    private final Map<String, Boolean> previous = new HashMap<>();

    public boolean pressed(String id, InputBinding binding, Minecraft client, boolean allowWhenScreenOpen) {
        boolean down = binding != null && binding.down(client);
        boolean old = previous.getOrDefault(id, false);
        previous.put(id, down);
        if (!allowWhenScreenOpen && client != null && client.gui != null && client.gui.screen() != null) return false;
        return down && !old;
    }

    public boolean released(String id, InputBinding binding, Minecraft client, boolean allowWhenScreenOpen) {
        boolean down = binding != null && binding.down(client);
        boolean old = previous.getOrDefault(id, false);
        previous.put(id, down);
        if (!allowWhenScreenOpen && client != null && client.gui != null && client.gui.screen() != null) return false;
        return !down && old;
    }

    public void clear() {
        previous.clear();
    }
}

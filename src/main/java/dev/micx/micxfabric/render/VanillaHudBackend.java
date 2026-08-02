package dev.micx.micxfabric.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class VanillaHudBackend implements HudBackend {
    @Override
    public void register(Identifier id, HudDrawer drawer) {
        HudElementRegistry.addLast(id, (graphics, deltaTracker) -> drawer.draw(graphics));
    }
}

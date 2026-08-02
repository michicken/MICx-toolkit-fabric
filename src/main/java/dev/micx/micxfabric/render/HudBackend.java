package dev.micx.micxfabric.render;

import net.minecraft.resources.Identifier;

public interface HudBackend {
    void register(Identifier id, HudDrawer drawer);
}

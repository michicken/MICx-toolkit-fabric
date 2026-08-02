package dev.micx.micxfabric.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface HudDrawer {
    void draw(GuiGraphicsExtractor graphics);
}

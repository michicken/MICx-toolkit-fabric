package dev.micx.micxfabric.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes Hud's chat component for client-only [T] translation output. */
@Mixin(Hud.class)
public interface HudChatAccess {
    @Accessor("chat")
    ChatComponent micx$chat();
}
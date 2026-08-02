package dev.micx.micxfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.Minecraft")
public interface RightClickDelayAccess {
    @Accessor("rightClickDelay")
    void micx$setRightClickDelay(int value);
}
